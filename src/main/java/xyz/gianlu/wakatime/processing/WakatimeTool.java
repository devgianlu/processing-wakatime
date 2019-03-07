package xyz.gianlu.wakatime.processing;

import processing.app.Base;
import processing.app.Sketch;
import processing.app.SketchCode;
import processing.app.tools.Tool;
import processing.app.ui.Editor;

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

    private void attachListener(Editor editor) {
        LOGGER.config("Attached to " + editor);
        editor.getTextArea().addCaretListener(e -> {
            Sketch sketch = editor.getSketch();
            SketchCode code = sketch.getCurrentCode();
            wakatime.appendHeartbeat(sketch.getName(), code.getFile().getAbsolutePath(), sketch.getMode(), true);
        });
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
