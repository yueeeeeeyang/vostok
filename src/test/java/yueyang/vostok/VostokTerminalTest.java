package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.terminal.VKTerminalConfig;
import yueyang.vostok.terminal.VKTerminalTheme;
import yueyang.vostok.terminal.component.VKDivider;
import yueyang.vostok.terminal.component.VKForm;
import yueyang.vostok.terminal.component.VKInput;
import yueyang.vostok.terminal.component.VKListView;
import yueyang.vostok.terminal.component.VKModal;
import yueyang.vostok.terminal.component.VKPanel;
import yueyang.vostok.terminal.component.VKStatusBar;
import yueyang.vostok.terminal.component.VKTableView;
import yueyang.vostok.terminal.component.VKTextView;
import yueyang.vostok.terminal.component.VKToast;
import yueyang.vostok.terminal.event.VKInputDecoder;
import yueyang.vostok.terminal.event.VKKey;
import yueyang.vostok.terminal.layout.VKGrid;
import yueyang.vostok.terminal.layout.VKHBox;
import yueyang.vostok.terminal.layout.VKVBox;
import yueyang.vostok.terminal.tool.VKAnsi;
import yueyang.vostok.terminal.tool.VKConsole;
import yueyang.vostok.terminal.tool.VKProgressBar;
import yueyang.vostok.terminal.tool.VKPrompt;
import yueyang.vostok.terminal.tool.VKSpinner;
import yueyang.vostok.terminal.tool.VKTablePrinter;
import yueyang.vostok.terminal.tool.VKTerminal;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VostokTerminalTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Terminal.close();
        } catch (Exception ignore) {
        }
        try {
            Vostok.close();
        } catch (Exception ignore) {
        }
    }

    @Test
    void testInitReinitAndTheme() {
        Vostok.Terminal.init(new VKTerminalConfig()
                .appName("Ops Console")
                .fps(30)
                .ansiEnabled(false)
                .theme(VKTerminalTheme.ocean()));

        assertTrue(Vostok.Terminal.started());
        assertEquals("Ops Console", Vostok.Terminal.config().getAppName());
        assertEquals(30, Vostok.Terminal.config().getFps());
        assertEquals(VKTerminalTheme.ocean().getBorder(), Vostok.Terminal.theme().getBorder());

        Vostok.Terminal.reinit(new VKTerminalConfig().appName("Ops Console 2").fps(12));
        assertEquals("Ops Console 2", Vostok.Terminal.config().getAppName());
        assertEquals(12, Vostok.Terminal.config().getFps());
    }

    @Test
    void testUnifiedInitIntegration() {
        Vostok.init(cfg -> cfg.terminalConfig(new VKTerminalConfig().appName("Unified Terminal")));
        assertTrue(Vostok.Terminal.started());
        assertEquals("Unified Terminal", Vostok.Terminal.config().getAppName());

        Vostok.close();
        assertFalse(Vostok.Terminal.started());
    }

    @Test
    void testTextWidthAndAnsi() {
        assertEquals(2, VKTextWidth.width("ab"));
        assertEquals(4, VKTextWidth.width("中文"));
        assertEquals("ab...", VKTextWidth.truncate("abcdef", 5));

        String styled = VKAnsi.apply("ok", true, VKAnsi.FG_GREEN, VKAnsi.BOLD);
        assertTrue(styled.contains("\u001B["));
        assertEquals("ok", VKAnsi.strip(styled));
        assertEquals("ok", VKAnsi.apply("ok", false, VKAnsi.FG_GREEN));
    }

    @Test
    void testTableProgressSpinnerAndConsole() {
        String table = Vostok.Terminal.table()
                .columns("ID", "Name")
                .row(1, "neo")
                .row(2, "trinity")
                .render();
        assertTrue(table.contains("ID"));
        assertTrue(table.contains("neo"));
        assertTrue(table.contains("+"));

        VKProgressBar bar = Vostok.Terminal.progressBar().width(10);
        assertTrue(bar.render(0.5, "half").contains("50%"));
        assertTrue(bar.render(2, null).contains("100%"));

        VKSpinner spinner = Vostok.Terminal.spinner();
        assertNotEquals(spinner.frame(0), spinner.frame(1));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        VKConsole console = new VKConsole(new PrintStream(bout, true, StandardCharsets.UTF_8), false, VKTerminalTheme.defaults())
                .timestampEnabled(false);
        console.info("hello");
        console.error("boom");
        String out = bout.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("[INFO] hello"));
        assertTrue(out.contains("[ERROR] boom"));
        assertFalse(out.contains("\u001B["));
    }

    @Test
    void testPromptHelpers() {
        ByteArrayInputStream in = new ByteArrayInputStream(("neo\ny\n2\n1,3\n").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bout, true, StandardCharsets.UTF_8);

        String name = VKPrompt.readLine("Name: ", in, out);
        boolean yes = VKPrompt.confirm("Continue", false, in, out);
        int idx = VKPrompt.choose("Mode", List.of("A", "B", "C"), 0, in, out);
        List<Integer> multi = VKPrompt.multiChoose("Targets", List.of("S1", "S2", "S3"), in, out);

        assertEquals("neo", name);
        assertTrue(yes);
        assertEquals(1, idx);
        assertEquals(List.of(0, 2), multi);

        String printed = bout.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Name:"));
        assertTrue(printed.contains("Continue"));
        assertTrue(printed.contains("Targets"));
    }

    @Test
    void testComponentLayoutAndAppRender() {
        Vostok.Terminal.reinit(new VKTerminalConfig()
                .appName("Render Demo")
                .ansiEnabled(false)
                .alternateScreen(false)
                .width(90)
                .height(40)
                .forceTty(true));

        VKPanel left = new VKPanel("Tasks")
                .child(new VKListView().items(List.of("Sync", "Export", "Cleanup")).selectedIndex(1));

        VKPanel right = new VKPanel("Stats")
                .child(new VKTableView().columns("Key", "Value")
                        .addRow("QPS", "120")
                        .addRow("Err", "0.1%"));

        VKForm form = new VKForm()
                .add(new VKInput("Username").value("neo"))
                .add(new VKInput("Password").value("secret").secret(true));

        VKGrid grid = new VKGrid().columns(2).hSpacing(2)
                .child(left)
                .child(right)
                .child(new VKPanel("Form").child(form))
                .child(new VKPanel("Log").child(new VKTextView("all good").muted()));

        VKVBox root = new VKVBox().spacing(1)
                .child(new VKTextView("Dashboard").title())
                .child(new VKDivider())
                .child(new VKHBox().spacing(2)
                        .child(new VKTextView("env=prod"))
                        .child(new VKTextView("region=us-east-1").muted()))
                .child(grid);

        String rendered = Vostok.Terminal.app()
                .root(root)
                .statusBar(new VKStatusBar("READY"))
                .toast(new VKToast("Saved").level(VKToast.Level.SUCCESS))
                .modal(new VKModal().title("Alert").message("Disk usage > 80%").visible(true))
                .render();

        assertTrue(rendered.contains("Dashboard"));
        assertTrue(rendered.contains("Tasks"));
        assertTrue(rendered.contains("Export"));
        assertTrue(rendered.contains("QPS"));
        assertTrue(rendered.contains("Saved"));
        assertTrue(rendered.contains("READY"));
        assertTrue(rendered.contains("Alert"));
    }

    @Test
    void testRunWithTerminalSequences() {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bout, true, StandardCharsets.UTF_8);

        Vostok.Terminal.reinit(new VKTerminalConfig()
                .ansiEnabled(true)
                .alternateScreen(true)
                .hideCursor(true)
                .forceTty(true)
                .output(out)
                .width(80)
                .height(24));

        Vostok.Terminal.app()
                .root(new VKTextView("Hello Terminal"))
                .run();

        String raw = bout.toString(StandardCharsets.UTF_8);
        assertTrue(raw.contains("Hello Terminal"));
        if (VKTerminal.supportsAnsi(Vostok.Terminal.config())) {
            assertTrue(raw.contains(VKAnsi.enterAltScreen()));
            assertTrue(raw.contains(VKAnsi.hideCursor()));
            assertTrue(raw.contains(VKAnsi.showCursor()));
            assertTrue(raw.contains(VKAnsi.exitAltScreen()));
        }
    }

    @Test
    void testInputDecoder() {
        VKInputDecoder decoder = new VKInputDecoder();

        assertEquals(VKKey.UP, decoder.decode("\u001B[A".getBytes(StandardCharsets.UTF_8)).key());
        assertEquals(VKKey.DOWN, decoder.decode("\u001B[B".getBytes(StandardCharsets.UTF_8)).key());
        assertEquals(VKKey.CTRL_C, decoder.decode(new byte[]{3}).key());
        assertEquals(VKKey.CHAR, decoder.decode("x".getBytes(StandardCharsets.UTF_8)).key());
        assertEquals('x', decoder.decode("x".getBytes(StandardCharsets.UTF_8)).ch());
    }

    @Test
    void testTablePrinterRoundedBorder() {
        VKTablePrinter printer = new VKTablePrinter()
                .columns("Name", "Score")
                .row("neo", "99")
                .borderStyle(VKTablePrinter.BorderStyle.ROUNDED)
                .padding(1);

        String rendered = printer.render();
        assertTrue(rendered.contains("╭"));
        assertTrue(rendered.contains("neo"));
        assertTrue(rendered.contains("Score"));
    }
}
