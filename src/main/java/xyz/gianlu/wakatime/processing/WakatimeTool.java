package xyz.gianlu.wakatime.processing;

import org.jetbrains.annotations.NotNull;
import processing.app.Base;
import processing.app.Sketch;
import processing.app.tools.Tool;
import processing.app.ui.Editor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Gianlu
 */
@SuppressWarnings("unused")
public class WakatimeTool implements Tool {
    private final static Logger LOGGER = Logger.getLogger(WakatimeTool.class.getName());
    private final List<Editor> knownEditors = new ArrayList<>();
    private Base base;
    private Wakatime wakatime;

    @Override
    public void init(Base base) {
        this.base = base;
        this.wakatime = new Wakatime(base.getActiveEditor());
        new Thread(new EditorDiscoverer()).start();

        LOGGER.info("WakatimeTool initialized!");
    }

    @Override
    public void run() {
        wakatime.showApiKeyPrompt(base.getActiveEditor());
    }

    @Override
    public String getMenuTitle() {
        return "Wakatime Tool";
    }

    private void attachListener(@NotNull Editor editor) {
        editor.getTextArea().addCaretListener(e -> appendHeartbeat(editor.getSketch(), false));

        JMenuBar bar = editor.getJMenuBar();
        JMenu file = bar.getMenu(0);
        if (file != null) {
            JMenuItem save = file.getItem(5);
            save.addActionListener(e -> appendHeartbeat(editor.getSketch(), true));
            JMenuItem saveAs = file.getItem(6);
            saveAs.addActionListener(e -> appendHeartbeat(editor.getSketch(), true));
        }

        LOGGER.config("Attached to " + editor);
    }

    private void appendHeartbeat(@NotNull Sketch sketch, boolean isWrite) {
        wakatime.appendHeartbeat(sketch.getName(), sketch.getCurrentCode().getFile().getAbsolutePath(), sketch.getMode(), isWrite);
    }

    private class EditorDiscoverer implements Runnable {

        @Override
        public void run() {
            if (base == null) return;

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }

                Editor active = base.getActiveEditor();
                if (active != null && !knownEditors.contains(active)) {
                    knownEditors.add(active);
                    attachListener(active);
                }
            }
        }
    }
}
