package com.lab.board3d;

import com.lab.board3d.rules.Move;
import com.lab.board3d.rules.Position;
import javafx.application.Platform;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * يشغّل محرك Python كعملية دائمة طوال اللعبة ويتكلم معه بأسطر نصية:
 *     نرسل FEN  <-  يرد بنقلة UCI
 *
 * - الاتصال يتم في خيط خلفي كي لا تتجمّد الواجهة أثناء تفكير Python.
 * - كل رد يُتحقَّق منه بمحرك القواعد. إذا كان فاسداً أو غير قانوني أو تأخر أو انهارت
 *   العملية، نلعب نقلة قانونية احتياطية ونكتب السبب في الـ console (واللعبة لا تتوقف).
 */
public final class PythonEngine implements GameController.Engine, AutoCloseable {

    private static final String EOF = "\u0000EOF";
    private static final long START_TIMEOUT_MS = 15_000;

    private final String pythonCommand;
    private final Path script;
    private final long moveTimeoutMs;
    private final Consumer<Runnable> uiThread;
    private final Consumer<String> log;
    private final Random random = new Random();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "python-engine");
        t.setDaemon(true);
        return t;
    });

    private volatile Process process;
    private BufferedWriter toPython;
    private BlockingQueue<String> fromPython;

    /** @param pythonCommand "python" (أو "py" / "python3" حسب جهازك)  @param script مسار engine_server.py */
    public PythonEngine(String pythonCommand, Path script, long moveTimeoutMs) {
        this(pythonCommand, script, moveTimeoutMs, Platform::runLater, System.out::println);
    }

    public PythonEngine(String pythonCommand, Path script, long moveTimeoutMs,
                 Consumer<Runnable> uiThread, Consumer<String> log) {
        this.pythonCommand = pythonCommand;
        this.script = script;
        this.moveTimeoutMs = moveTimeoutMs;
        this.uiThread = uiThread;
        this.log = log;
    }

    /** يشغّل Python مسبقاً (في الخلفية) كي لا تتأخر أول نقلة، وتظهر أخطاء التشغيل مبكراً. */
    public void warmUp() {
        worker.submit(() -> {
            try {
                ensureStarted();
                log.accept("[PythonEngine] Python is ready (READY received)");
            } catch (Exception e) {
                log.accept("[PythonEngine] cannot start Python: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------ Engine

    @Override
    public void chooseMove(Position position, Consumer<Move> callback) {
        String fen = position.toFen();
        worker.submit(() -> {
            Move chosen = null;
            String problem = null;
            long t0 = System.currentTimeMillis();
            try {
                log.accept("[Java -> Python] FEN: " + fen);
                String uci = request(fen);
                long ms = System.currentTimeMillis() - t0;
                log.accept("[Python -> Java] UCI: " + uci + "   (" + ms + " ms)");

                Move m = Move.fromUci(uci);
                chosen = position.findLegal(m.from(), m.to(), m.promotion());
                if (chosen == null) problem = "illegal move from Python: " + uci;
                else log.accept("[Java] OK: legal move accepted -> " + chosen);
            } catch (Exception e) {
                problem = e.toString();
            }
            if (chosen == null) {
                List<Move> all = position.legalMoves();
                chosen = all.get(random.nextInt(all.size()));
                log.accept("[Java] FAILED: " + problem + "  -> using fallback move " + chosen);
            }
            Move result = chosen;
            uiThread.accept(() -> callback.accept(result));
        });
    }

    // ------------------------------------------------------------ process handling

    private String request(String fen) throws IOException, InterruptedException {
        ensureStarted();
        fromPython.clear();
        toPython.write(fen);
        toPython.newLine();
        toPython.flush();

        String line = fromPython.poll(moveTimeoutMs, TimeUnit.MILLISECONDS);
        if (line == null) {
            stopProcess();   // العملية عالقة: نقتلها، وستُعاد عند الطلب التالي
            throw new IOException("Python did not answer within " + moveTimeoutMs + " ms");
        }
        if (EOF.equals(line)) {
            stopProcess();
            throw new IOException("Python process died (see its error in the console)");
        }
        return line.trim();
    }

    private void ensureStarted() throws IOException, InterruptedException {
        if (process != null && process.isAlive()) return;

        ProcessBuilder pb = script.getFileName().toString().endsWith(".exe")
                ? new ProcessBuilder(script.toAbsolutePath().toString())
                : new ProcessBuilder(pythonCommand, "-u", script.getFileName().toString());
        pb.directory(script.toAbsolutePath().getParent().toFile());
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);   // أخطاء Python تظهر في console
        Process p = pb.start();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try {
                String l;
                while ((l = reader.readLine()) != null) queue.add(l);
            } catch (IOException ignored) {
            }
            queue.add(EOF);
        }, "python-reader");
        t.setDaemon(true);
        t.start();

        process = p;
        fromPython = queue;
        toPython = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));

        String hello = queue.poll(START_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!"READY".equals(hello)) {
            stopProcess();
            throw new IOException("Python did not say READY (got: " + hello + ")");
        }
    }

    private void stopProcess() {
        Process p = process;
        if (p != null) p.destroyForcibly();
        process = null;
    }

    /** أغلق عند إغلاق النافذة كي لا تبقى عملية Python شغّالة. */
    @Override
    public void close() {
        worker.shutdownNow();
        stopProcess();
    }
}
