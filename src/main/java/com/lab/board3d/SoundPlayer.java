package com.lab.board3d;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * يشغّل المؤثرات الصوتية من مجلد sounds (بجانب models و python).
 *
 *  - يستخدم javax.sound المدمج في Java، فلا يحتاج مكتبة إضافية في pom.xml.
 *  - عند التحميل يقصّ الصمت من بداية كل ملف ومن نهايته (وإلا تأخر الصوت، وبقي الـ Clip "شغّالاً"
 *    صامتاً فتعثّر الصوت التالي).
 *  - كل تشغيل يستخدم Clip جديداً يُغلق عند انتهائه. إعادة تشغيل Clip قديم (stop ثم start)
 *    غير موثوقة في Java Sound، وكانت سبب غياب صوت نقلات الحاسوب.
 *  - التشغيل في خيط خاص كي لا يتأخر خيط الواجهة.
 *  - لا يُسقط اللعبة أبداً: لو لا يوجد ملف أو بطاقة صوت، تستمر اللعبة بدون صوت.
 */
public final class SoundPlayer implements AutoCloseable {

    /** يُنشئ Clip جديداً (قابل للاستبدال في الاختبار). */
    @FunctionalInterface
    interface ClipFactory {
        Clip create() throws Exception;
    }

    /** صوت محمّل في الذاكرة بعد القص. */
    private record Sample(AudioFormat format, byte[] data, int offset, int length) {}

    /** أسماء الملفات المقبولة لكل مؤثر (الاسم الثاني لأن الملف المرفق فيه خطأ إملائي Gmae). */
    private static final Map<GameSound, List<String>> FILES = Map.of(
            GameSound.GAME_START, List.of("Game_start.wav", "Gmae start.wav"),
            GameSound.MOVE, List.of("Move.wav"),
            GameSound.CAPTURE, List.of("Capture.wav"),
            GameSound.CHECK, List.of("Check.wav"),
            GameSound.CHECKMATE, List.of("Checkmate.wav"),
            GameSound.DRAW, List.of("Draw.wav"));

    private final EnumMap<GameSound, Sample> samples = new EnumMap<>(GameSound.class);
    private final ClipFactory clipFactory;
    private final Set<Clip> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService audio = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sound-player");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean muted;

    public SoundPlayer(Path soundsDir) {
        this(soundsDir, AudioSystem::getClip);
    }

    SoundPlayer(Path soundsDir, ClipFactory clipFactory) {
        this.clipFactory = clipFactory;
        for (GameSound s : GameSound.values()) {
            load(s, soundsDir);
        }
        System.out.println("[Sound] loaded " + samples.size() + " of " + GameSound.values().length
                + " sounds from " + soundsDir.toAbsolutePath());
        audio.execute(this::probeDevice);
    }

    /** يتأكد من وجود جهاز صوت ويكتب النتيجة مرة واحدة عند التشغيل. */
    private void probeDevice() {
        try {
            clipFactory.create().close();
            System.out.println("[Sound] audio device OK");
        } catch (Exception e) {
            System.err.println("[Sound] NO audio device available: " + e);
        }
    }

    private void load(GameSound sound, Path dir) {
        File file = null;
        for (String name : FILES.get(sound)) {
            File f = dir.resolve(name).toFile();
            if (f.isFile()) { file = f; break; }
        }
        if (file == null) {
            System.err.println("[Sound] file not found for " + sound + " in " + dir.toAbsolutePath()
                    + "  (expected " + FILES.get(sound) + ")");
            return;
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat fmt = in.getFormat();
            byte[] data = in.readAllBytes();
            int[] range = audibleRange(data, fmt);
            samples.put(sound, new Sample(fmt, data, range[0], range[1] - range[0]));
        } catch (Exception e) {
            System.err.println("[Sound] cannot load " + file.getName() + ": " + e);
        }
    }

    // =====================================================================

    public void play(GameSound sound) {
        if (muted) return;
        Sample s = samples.get(sound);
        if (s == null) return;
        System.out.println("[Sound] play " + sound);   // للتشخيص: يظهر عند كل صوت مطلوب
        audio.execute(() -> playNow(sound, s));
    }

    private void playNow(GameSound sound, Sample s) {
        try {
            Clip clip = clipFactory.create();
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {   // انتهى الصوت: حرّر الجهاز
                    active.remove(clip);
                    clip.close();
                }
            });
            clip.open(s.format(), s.data(), s.offset(), s.length());
            active.add(clip);
            clip.start();
        } catch (Exception e) {
            System.err.println("[Sound] cannot play " + sound + ": " + e);
        }
    }

    public boolean toggleMute() {
        muted = !muted;
        System.out.println("[Sound] " + (muted ? "muted" : "unmuted"));
        return muted;
    }

    @Override
    public void close() {
        audio.shutdownNow();
        for (Clip c : active) {
            try { c.close(); } catch (Exception ignored) { }
        }
        active.clear();
    }

    // =====================================================================

    /**
     * يُرجع {بداية، نهاية} الجزء المسموع من تسجيل PCM 16-bit، بالبايت ومحاذاة لحدود الإطار.
     * "مسموع" = أعلى من 2% من أعلى قمة في الملف. نُبقي 15 ملّي ثانية قبل البداية و100 بعد النهاية.
     * للصيغ الأخرى أو الصمت الكامل: الملف كله بدون قص.
     */
    static int[] audibleRange(byte[] pcm, AudioFormat fmt) {
        int whole = pcm.length - pcm.length % Math.max(1, fmt.getFrameSize());
        int[] all = {0, whole};
        if (fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || fmt.getSampleSizeInBits() != 16 || fmt.isBigEndian() || fmt.getFrameSize() <= 0) return all;

        int peak = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) peak = Math.max(peak, Math.abs(sample(pcm, i)));
        int threshold = Math.max(100, peak / 50);

        int frameSize = fmt.getFrameSize();
        int first = -1, last = -1;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            if (Math.abs(sample(pcm, i)) > threshold) {
                if (first < 0) first = i / frameSize;
                last = i / frameSize;
            }
        }
        if (first < 0) return all;   // صمت كامل

        int rate = (int) fmt.getFrameRate();
        int startFrame = Math.max(0, first - rate * 15 / 1000);
        int endFrame = Math.min(whole / frameSize, last + 1 + rate * 100 / 1000);
        return new int[]{startFrame * frameSize, endFrame * frameSize};
    }

    private static int sample(byte[] b, int i) {
        return (short) ((b[i + 1] << 8) | (b[i] & 0xff));
    }
}
