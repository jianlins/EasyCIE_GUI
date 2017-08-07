package edu.utah.bmi.simple.gui.entry;

import com.sun.javafx.binding.ExpressionHelper;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.lang.ref.WeakReference;
import java.util.Set;

/**
 * @author Jianlin Shi
 *         Created on 2/26/17.
 */
public class ObservableSetting extends ObjectProperty {
    private Setting value;
    private ObservableValue<? extends Setting> observable = null;
    private InvalidationListener listener = null;
    private boolean valid = true;
    private ExpressionHelper<Setting> helper = null;

    public ObservableSetting(Setting initialValue) {
        this.value = initialValue;

    }


    @Override
    public void bind(ObservableValue newObservable) {
        if (newObservable == null) {
            throw new NullPointerException("Cannot bind to null");
        }
        if (!newObservable.equals(observable)) {
            unbind();
            observable = newObservable;
            if (listener == null) {
                listener = new Listener(this);
            }
            observable.addListener(listener);
            markInvalid();
        }
    }

    @Override
    public void unbind() {
        if (observable != null) {
            value = observable.getValue();
            observable.removeListener(listener);
            observable = null;
        }

    }

    @Override
    public boolean isBound() {
        return observable != null;
    }

    @Override
    public Object getBean() {
        valid = true;
        return observable == null ? value : observable.getValue();
    }

    @Override
    public String getName() {
        if (valid)
            return value.getSettingName();
        else
            return null;
    }

    @Override
    public Object get() {
        return value;
    }

    @Override
    public void set(Object newValue) {
        if (isBound()) {
            throw new java.lang.RuntimeException((getBean() != null && getName() != null ?
                    getBean().getClass().getSimpleName() + "." + getName() + " : " : "") + "A bound value cannot be set.");
        }
        if ((value == null) ? newValue != null : (newValue instanceof Setting && !value.serialize().equals(((Setting) newValue).serialize()))) {
            value = (Setting) newValue;
            markInvalid();
        }
    }

    @Override
    public void addListener(ChangeListener listener) {
        helper = ExpressionHelper.addListener(helper, this, listener);
    }

    @Override
    public void removeListener(ChangeListener listener) {
        helper = ExpressionHelper.removeListener(helper, listener);

    }

    @Override
    public void addListener(InvalidationListener listener) {
        helper = ExpressionHelper.addListener(helper, this, listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper = ExpressionHelper.removeListener(helper, listener);

    }

    protected void fireValueChangedEvent() {
        ExpressionHelper.fireValueChangedEvent(helper);
    }

    private void markInvalid() {
        if (valid) {
            valid = false;
            fireValueChangedEvent();
        }
    }

    private static class Listener implements InvalidationListener {

        private final WeakReference<ObservableSetting> wref;

        public Listener(ObservableSetting ref) {
            this.wref = new WeakReference<>(ref);
        }

        @Override
        public void invalidated(Observable observable) {
            ObservableSetting ref = wref.get();
            if (ref == null) {
                observable.removeListener(this);
            } else {
                ref.markInvalid();
            }
        }
    }
}
