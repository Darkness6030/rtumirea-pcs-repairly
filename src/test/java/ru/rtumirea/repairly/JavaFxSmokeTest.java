package ru.rtumirea.repairly;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import ru.rtumirea.repairly.fx.*;
import ru.rtumirea.repairly.fx.controller.*;
import ru.rtumirea.repairly.model.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real toolkit + FXML + asynchronous JDBC. Explicit profile: mvnw -Pgui-tests test. */
@Tag("gui")
class JavaFxSmokeTest extends DatabaseTestSupport {
    private FxTasks tasks;
    private Workspace workspace;
    private Stage stage;
    private Parent root;

    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @AfterAll static void stopToolkit() { Platform.exit(); }

    @AfterEach void closeWindows() throws Exception {
        fx(() -> {
            for (var window : List.copyOf(javafx.stage.Window.getWindows())) window.hide();
            if (tasks != null) tasks.close();
            return null;
        });
    }

    private void openMain() throws Exception {
        fx(() -> {
            tasks = new FxTasks();
            stage = new Stage();
            workspace = new Workspace(database, tasks, stage);
            var view = Ui.<MainController>load("main.fxml");
            root = view.root();
            Scene scene = new Scene(root, 1280, 820);
            Ui.style(scene);
            stage.setScene(scene);
            view.controller().configure(workspace);
            stage.show();
            workspace.refresh();
            return null;
        });
        await(() -> workspace.loaded.get() && !workspace.busy.get());
    }

    @Test void mainFxmlSearchFiltersSortRelatedViewsAndStatistics() throws Exception {
        var first = requests.create(new RequestInput(client.id(), technician.id(), "Холодильник", "Atlant", "XM", "Не охлаждает", new BigDecimal("900"), RepairStatus.NEW));
        var second = requests.create(new RequestInput(client.id(), null, "Пылесос", "Bosch", "B1", "Не включается", new BigDecimal("100"), RepairStatus.NEW));
        openMain();
        fx(() -> {
            Parent requestsPage = (Parent) root.lookup("#requestsPage");
            TableView<RequestView> table = lookup(requestsPage, "#table");
            assertEquals(2, table.getItems().size());
            table.getSelectionModel().selectFirst();
            snapshot(root, "requests");
            TextField search = lookup(requestsPage, "#search");
            search.setText("ATLANT");
            assertEquals(first.id(), table.getItems().getFirst().request().id());
            search.clear();
            ComboBox<?> technicians = lookup(requestsPage, "#technicianFilter");
            technicians.getSelectionModel().select(1); // Not assigned.
            assertEquals(List.of(second.id()), table.getItems().stream().map(r -> r.request().id()).toList());
            technicians.getSelectionModel().selectFirst();
            DatePicker from = lookup(requestsPage, "#fromDate"), to = lookup(requestsPage, "#toDate");
            from.setValue(LocalDate.now()); to.setValue(LocalDate.now().minusDays(1));
            assertTrue(table.getItems().isEmpty());
            assertFalse(((Label) requestsPage.lookup("#filterError")).getText().isBlank());
            assertTrue(requestsPage.lookup("#exportButton").isDisabled());
            from.setValue(null); to.setValue(null);
            var cost = table.getColumns().stream().filter(c -> c.getText().equals("Стоимость")).findFirst().orElseThrow();
            cost.setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().setAll(List.of(cost)); table.sort();
            assertEquals(second.id(), table.getItems().getFirst().request().id());
            table.getSelectionModel().selectFirst();
            assertTrue(((Label) requestsPage.lookup("#detailClient")).getText().contains(client.phone()));
            ((ToggleButton) root.lookup("#clientsNav")).fire();
            Parent directory = (Parent) root.lookup("#clientsPage");
            assertTrue(directory.isVisible());
            TableView<?> people = lookup(directory, "#table"); people.getSelectionModel().selectFirst();
            ((Button) directory.lookup("#relatedButton")).fire();
            assertTrue(requestsPage.isVisible());
            assertEquals(2, table.getItems().size());
            ((ToggleButton) root.lookup("#statisticsNav")).fire();
            Parent statistics = (Parent) root.lookup("#statisticsPage");
            assertEquals("2", ((Label) statistics.lookup("#total")).getText());
            assertEquals("2", ((Label) statistics.lookup("#active")).getText());
            snapshot(root, "statistics");
            // Layout must remain usable at the advertised minimum dimensions.
            stage.setWidth(1050); stage.setHeight(700); root.applyCss(); root.layout();
            assertTrue(statistics.getBoundsInParent().getMaxX() <= root.getLayoutBounds().getWidth());
            return null;
        });
        await(() -> stage.getScene().getWidth() <= 1050 && stage.getScene().getHeight() <= 700);
        fx(() -> {
            snapshot(root, "statistics-small");
            ((ToggleButton) root.lookup("#requestsNav")).fire();
            snapshot(root, "requests-small");
            return null;
        });
    }

    @Test void requestFormKeepsInvalidInputThenSavesAndEditsThroughService() throws Exception {
        openMain();
        var view = fx(() -> Ui.<RequestDialogController>load("request-dialog.fxml"));
        Stage dialog = fx(() -> {
            Stage result = Ui.dialog(stage, "Тестовая заявка", view.root());
            view.controller().configure(workspace, result, null);
            result.show();
            ComboBox<Client> clientInput = lookup(view.root(), "#client"); clientInput.setValue(client);
            ((TextField) view.root().lookup("#applianceType")).setText("Печь");
            ((TextField) view.root().lookup("#brand")).setText("Brand");
            ((TextField) view.root().lookup("#model")).setText("Model");
            ((TextArea) view.root().lookup("#problem")).setText("Описание\nВторая строка");
            ((TextField) view.root().lookup("#cost")).setText("-12");
            button(view.root(), "Сохранить").fire();
            assertFalse(((Label) view.root().lookup("#error")).getText().isBlank());
            assertTrue(result.isShowing());
            snapshot(view.root(), "request-form-error");
            ((TextField) view.root().lookup("#cost")).setText("120,50");
            button(view.root(), "Сохранить").fire();
            return result;
        });
        await(() -> !dialog.isShowing() && !workspace.busy.get());
        var saved = requests.findAll().getFirst();
        assertEquals(new BigDecimal("120.50"), saved.cost());
        assertEquals(1, fx(() -> workspace.requestRows.size()));
        var edit = fx(() -> Ui.<RequestDialogController>load("request-dialog.fxml"));
        Stage editStage = fx(() -> {
            var result = Ui.dialog(stage, "Редактирование", edit.root());
            edit.controller().configure(workspace, result, new RequestView(saved, client, null));
            result.show();
            assertEquals("Печь", ((TextField) edit.root().lookup("#applianceType")).getText());
            ComboBox<Technician> technicianInput = lookup(edit.root(), "#technician"); technicianInput.setValue(technician);
            ComboBox<RepairStatus> status = lookup(edit.root(), "#status"); status.setValue(RepairStatus.DIAGNOSING);
            button(edit.root(), "Сохранить").fire();
            return result;
        });
        await(() -> !editStage.isShowing() && !workspace.busy.get());
        assertEquals(RepairStatus.DIAGNOSING, requests.findById(saved.id()).status());
        assertEquals(technician.id(), requests.findById(saved.id()).technicianId());
    }

    @Test void directoryFormValidatesRequiredFieldsAndUniquePhone() throws Exception {
        openMain();
        var view = fx(() -> Ui.<PersonDialogController>load("person-dialog.fxml"));
        var dialog = fx(() -> {
            var result = Ui.dialog(stage, "Тестовый клиент", view.root());
            view.controller().configure(workspace, result, DirectoryController.Kind.CLIENT, null);
            result.show();
            snapshot(view.root(), "person-form");
            button(view.root(), "Сохранить").fire();
            return result;
        });
        await(() -> !view.root().isDisabled());
        assertTrue(fx(() -> ((Label) view.root().lookup("#error")).getText().contains("Имя")));
        fx(() -> {
            ((TextField) view.root().lookup("#name")).setText("Новый клиент");
            ((TextField) view.root().lookup("#phone")).setText(client.phone());
            ((TextField) view.root().lookup("#detail")).setText("Учебный адрес");
            button(view.root(), "Сохранить").fire(); return null;
        });
        await(() -> !view.root().isDisabled());
        assertTrue(fx(() -> ((Label) view.root().lookup("#error")).getText().contains("телефоном")));
        fx(() -> {
            ((TextField) view.root().lookup("#phone")).setText("+70000000002");
            button(view.root(), "Сохранить").fire(); return null;
        });
        await(() -> !dialog.isShowing() && !workspace.busy.get());
        assertEquals(2, clients.findAll().size());
        assertEquals(2, fx(() -> workspace.clientRows.size()));
    }

    @Test void deletionRequiresConfirmationAndRefreshesTheTable() throws Exception {
        var request = requests.create(client.id(), "Печь", "Brand", "Model", "Не включается");
        openMain();
        fx(() -> {
            Parent page = (Parent) root.lookup("#requestsPage");
            TableView<RequestView> table = lookup(page, "#table");
            table.getSelectionModel().selectFirst();
            answerNextConfirmation(ButtonBar.ButtonData.CANCEL_CLOSE);
            ((Button) page.lookup("#deleteButton")).fire();
            assertEquals(1, table.getItems().size());
            return null;
        });
        assertNotNull(requests.findById(request.id()));
        fx(() -> {
            Parent page = (Parent) root.lookup("#requestsPage");
            answerNextConfirmation(ButtonBar.ButtonData.OK_DONE);
            ((Button) page.lookup("#deleteButton")).fire();
            return null;
        });
        await(() -> !workspace.busy.get() && workspace.requestRows.isEmpty());
        assertTrue(requests.findAll().isEmpty());
        assertTrue(fx(() -> root.lookup("#requestsPage").lookup("#deleteButton").isDisabled()));
    }

    private void answerNextConfirmation(ButtonBar.ButtonData answer) {
        Platform.runLater(() -> {
            for (var window : List.copyOf(javafx.stage.Window.getWindows())) {
                if (window == stage) continue;
                var pane = (DialogPane) window.getScene().lookup(".dialog-pane");
                if (pane != null) {
                    var type = pane.getButtonTypes().stream().filter(t -> t.getButtonData() == answer).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(type)).fire();
                    return;
                }
            }
            throw new AssertionError("Expected deletion confirmation dialog");
        });
    }

    private static Button button(Parent root, String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getText().equals(text)).findFirst().orElseThrow();
    }

    /** Optional component renders for layout review; no screen capture or native input. */
    private static void snapshot(Parent content, String name) throws Exception {
        String directory = System.getProperty("repairly.screenshots");
        if (directory == null) return;
        content.applyCss(); content.layout();
        var rendered = content.snapshot(null, null);
        int width = (int) rendered.getWidth(), height = (int) rendered.getHeight();
        var pixels = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var reader = rendered.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.setRGB(x, y, reader.getArgb(x, y));
        Path path = Path.of(directory); Files.createDirectories(path);
        javax.imageio.ImageIO.write(pixels, "png", path.resolve(name + ".png").toFile());
    }

    @SuppressWarnings("unchecked") private static <T> T lookup(Parent root, String id) { return (T) root.lookup(id); }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) fail("Asynchronous JavaFX operation timed out");
            Thread.sleep(30);
        }
    }
}
