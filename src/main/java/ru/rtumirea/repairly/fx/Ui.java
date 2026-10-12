package ru.rtumirea.repairly.fx;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import javafx.util.StringConverter;
import ru.rtumirea.repairly.exception.RepairlyException;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

public final class Ui {
    private Ui() {}
    private static final String RESOURCES = "/ru/rtumirea/repairly/fx/";
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public record View<T>(Parent root, T controller) {}

    public static <T> View<T> load(String name) {
        var resource = Ui.class.getResource(RESOURCES + name);
        if (resource == null) throw new RepairlyException("Не найден ресурс интерфейса: " + name);
        FXMLLoader loader = new FXMLLoader(resource);
        try { return new View<>(loader.load(), loader.getController()); }
        catch (IOException e) { throw new RepairlyException("Не удалось открыть окно приложения.", e); }
    }

    public static void style(Scene scene) {
        scene.getStylesheets().add(Ui.class.getResource(RESOURCES + "app.css").toExternalForm());
    }

    public static Stage dialog(Window owner, String title, Parent content) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        Scene scene = new Scene(content);
        style(scene);
        stage.setScene(scene);
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        return stage;
    }

    public static String message(Throwable error) {
        if (error instanceof RepairlyException) return error.getMessage();
        return "Не удалось выполнить действие. Попробуйте обновить данные. Подробности записаны в журнал.";
    }

    public static void error(Window owner, Throwable error) {
        org.slf4j.LoggerFactory.getLogger(Ui.class).warn("Operation failed", error);
        alert(owner, Alert.AlertType.ERROR, "Не удалось выполнить действие", message(error));
    }

    public static void alert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Repairly");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(480);
        alert.getDialogPane().getStylesheets().add(Ui.class.getResource(RESOURCES + "app.css").toExternalForm());
        alert.showAndWait();
    }

    public static boolean confirm(Window owner, String title, String message) {
        ButtonType yes = new ButtonType("Подтвердить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yes, cancel);
        alert.initOwner(owner);
        alert.setTitle("Repairly");
        alert.setHeaderText(title);
        return alert.showAndWait().filter(yes::equals).isPresent();
    }

    public static <T, V> TableColumn<T, V> column(String title, double width, Function<T, V> value) {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }

    public static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : label.apply(value); }
            @Override public T fromString(String text) { throw new UnsupportedOperationException("Selection only"); }
        };
    }

    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value);
    }

    public static <T> void dates(TableColumn<T, LocalDateTime> column) {
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : DATE.format(value));
            }
        });
    }
}
