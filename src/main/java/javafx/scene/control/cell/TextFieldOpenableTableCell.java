package javafx.scene.control.cell;

import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Cell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.SettingValueConverter;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;


public class TextFieldOpenableTableCell<S, T> extends TableCell<S, T> {

    private TaskFX currentTask;
    private Object[] item;
    private Setting setting;


    /***************************************************************************
     *                                                                         *
     * Static cell factories                                                   *
     *                                                                         *
     **************************************************************************/

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forTableColumn() {
        return forTableColumn(new SettingValueConverter());
    }


    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> forTableColumn(
            final StringConverter<T> converter) {
        return list -> new TextFieldOpenableTableCell<S, T>(converter);
    }


    /***************************************************************************
     *                                                                         *
     * Fields                                                                  *
     *                                                                         *
     **************************************************************************/

    private TextField textField;


    /***************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a default TextFieldTableCell with a null converter. Without a
     * {@link StringConverter} specified, this cell will not be able to accept
     * input from the TextField (as it will not know how to convert this back
     * to the domain object). It is therefore strongly encouraged to not use
     * this constructor unless you intend to set the converter separately.
     */
    public TextFieldOpenableTableCell() {
        this(null);
    }

    /**
     * Creates a TextFieldTableCell that provides a {@link TextField} when put
     * into editing mode that allows editing of the cell content. This method
     * will work on any TableColumn instance, regardless of its generic type.
     * However, to enable this, a {@link StringConverter} must be provided that
     * will convert the given String (from what the user typed in) into an
     * instance of type T. This item will then be passed along to the
     * {@link TableColumn#onEditCommitProperty()} callback.
     *
     * @param converter A {@link StringConverter converter} that can convert
     *                  the given String (from what the user typed in) into an instance of
     *                  type T.
     */
    public TextFieldOpenableTableCell(StringConverter<T> converter) {
        this.getStyleClass().add("text-field-table-cell");
        setConverter(converter);
    }

    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    // --- converter
    private ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<StringConverter<T>>(this, "converter");

    /**
     * The {@link StringConverter} property.
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Sets the {@link StringConverter} to be used in this cell.
     */
    public final void setConverter(StringConverter<T> value) {
        converterProperty().set(value);
    }

    /**
     * Returns the {@link StringConverter} used in this cell.
     */
    public final StringConverter<T> getConverter() {
        return converterProperty().get();
    }


    /***************************************************************************
     *                                                                         *
     * Public API                                                              *
     *                                                                         *
     **************************************************************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public void startEdit() {
        if (!isEditable()
                || !getTableView().isEditable()
                || !getTableColumn().isEditable()) {
            return;
        }

        String openApp = setting.getOpenClick();

        if (openApp != null && openApp.length() > 0) {
            File file = new File(setting.getSettingValue());
            javafx.concurrent.Task thisTask = null;
            Class<? extends javafx.concurrent.Task> c = null;
            try {
                System.out.println(openApp);
                c = Class.forName(openApp).asSubclass(javafx.concurrent.Task.class);
                Constructor<? extends Task> taskConstructor;
                if (file.exists()) {
                    taskConstructor = c.getConstructor(TaskFX.class, Setting.class);
                    thisTask = taskConstructor.newInstance(currentTask, setting);
                    thisTask.run();
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            super.startEdit();
            if (isEditing()) {
                if (textField == null) {
                    textField = createTextField(this, getConverter());
                }
                startEdit(this, getConverter(), null, null, textField);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        if (setting.getOpenClick() == null || setting.getOpenClick().length() == 0) {
            this.setText(getItemText(this, getConverter()));
            this.setGraphic(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (!empty) {
            this.item = (Object[]) item;
            currentTask = (TaskFX) this.item[0];
            addText((Setting) this.item[1]);
        } else {
            setText("");
        }
        ((SettingValueConverter<T>) getConverter()).setItem(this.item);
//        Class<CellUtils> c = CellUtils.class;
//        if (setting.getOpenClick()==null || setting.getOpenClick().length() == 0)
        updateItem(this, getConverter(), null, null, textField);
    }

    private void addText(Setting setting) {
        this.setting = setting;
        String value = setting.getSettingValue();
        setText(value);

    }

    private void updateItem(final Cell<T> cell,
                            final StringConverter<T> converter,
                            final HBox hbox,
                            final Node graphic,
                            final TextField textField) {
        if (cell.isEmpty()) {
            cell.setText(null);
            cell.setGraphic(null);
        } else {
            if (cell.isEditing()) {
                if (textField != null) {
                    textField.setText(getItemText(cell, converter));
                }
                cell.setText(null);

                if (graphic != null) {
                    hbox.getChildren().setAll(graphic, textField);
                    cell.setGraphic(hbox);
                } else {
                    cell.setGraphic(textField);
                }
            } else {
                cell.setText(getItemText(cell, converter));
                cell.setGraphic(graphic);
            }
        }
    }

    private <T> String getItemText(Cell<T> cell, StringConverter<T> converter) {
        return converter == null ?
                cell.getItem() == null ? "" : cell.getItem().toString() :
                converter.toString(cell.getItem());
    }

    private <T> void startEdit(final Cell<T> cell,
                               final StringConverter<T> converter,
                               final HBox hbox,
                               final Node graphic,
                               final TextField textField) {
        if (textField != null) {
            textField.setText(getItemText(cell, converter));
        }
        cell.setText(null);

        if (graphic != null) {
            hbox.getChildren().setAll(graphic, textField);
            cell.setGraphic(hbox);
        } else {
            cell.setGraphic(textField);
        }

        textField.selectAll();

        // requesting focus so that key input can immediately go into the
        // TextField (see RT-28132)
        textField.requestFocus();
    }

    private <T> TextField createTextField(final Cell<T> cell, final StringConverter<T> converter) {
        final TextField textField = new TextField(getItemText(cell, converter));

        // Use onAction here rather than onKeyReleased (with check for Enter),
        // as otherwise we encounter RT-34685
        textField.setOnAction(event -> {
            if (converter == null) {
                throw new IllegalStateException(
                        "Attempting to convert text input into Object, but provided "
                                + "StringConverter is null. Be sure to set a StringConverter "
                                + "in your cell factory.");
            }
            cell.commitEdit(converter.fromString(textField.getText()));
            event.consume();
        });
        textField.setOnKeyReleased(t -> {
            if (t.getCode() == KeyCode.ESCAPE) {
                cell.cancelEdit();
                t.consume();
            }
        });
        return textField;
    }

    public String toString() {
        if (item != null && item.length > 1)
            return ((Setting) item[1]).getSettingValue();
        else
            return "";
    }
}
