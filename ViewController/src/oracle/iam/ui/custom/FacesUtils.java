package oracle.iam.ui.custom;

import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;
import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.faces.application.Application;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import oracle.adf.model.BindingContext;
import oracle.adf.model.binding.DCBindingContainer;
import oracle.adf.model.binding.DCControlBinding;
import oracle.adf.view.rich.context.AdfFacesContext;
import oracle.binding.AttributeBinding;
import oracle.binding.ControlBinding;
import oracle.iam.ui.platform.utils.TaskFlowUtils; // /home/oracle/Oracle/Middleware/Oracle_IDM1/server/modules/oracle.iam.ui.view_11.1.2/adflibPlatformUI.jar
import oracle.jbo.uicli.binding.JUCtrlActionBinding;
import oracle.jbo.uicli.binding.JUCtrlListBinding;
import oracle.jbo.uicli.binding.JUEventBinding;


public class FacesUtils {
    private FacesUtils() {
        // do not instantiate
        throw new AssertionError();
    }

    /*
     * Re-render the component.
     */
    public static void partialRender(UIComponent component) {
        if (component != null) {
            AdfFacesContext.getCurrentInstance().addPartialTarget(component);
        }
    }

    /*
     * Sets attribute value through attribute binding.
     */
    public static void setAttributeBindingValue(String attributeName,
                                                Object value) {
        AttributeBinding binding = getAttributeBinding(attributeName);
        if (binding != null) {
            binding.setInputValue(value);
        } else {
            throw new IllegalArgumentException("Binding " + attributeName +
                                               " does not exist.");
        }
    }

    /*
     * Gets attribute value using attribute binding.
     */
    public static <T> T getAttributeBindingValue(String attributeName,
                                                 Class<T> clazz) {
        AttributeBinding binding = getAttributeBinding(attributeName);
        if (binding != null) {
            return (T)binding.getInputValue();
        } else {
            throw new IllegalArgumentException("Binding " + attributeName +
                                               " does not exist.");
        }
    }

    /*
     * Gets attribute value using list binding.
     */
    public static <T> T getListBindingValue(String attributeName,
                                            Class<T> clazz) {
        ControlBinding ctrlBinding = getControlBinding(attributeName);
        if (ctrlBinding instanceof JUCtrlListBinding) {
            JUCtrlListBinding listBinding = (JUCtrlListBinding)ctrlBinding;
            return (T)listBinding.getAttributeValue();
        } else {
            throw new IllegalArgumentException("Binding " + attributeName +
                                               " is not list binding.");
        }
    }

    public static ControlBinding getControlBinding(String name) {
        ControlBinding crtlBinding = getBindings().getControlBinding(name);
        if (crtlBinding == null) {
            throw new IllegalArgumentException("Control Binding '" + name +
                                               "' not found");
        }
        return crtlBinding;
    }

    public static AttributeBinding getAttributeBinding(String name) {
        ControlBinding ctrlBinding = getControlBinding(name);
        AttributeBinding attributeBinding = null;
        if (ctrlBinding != null) {
            if (ctrlBinding instanceof AttributeBinding) {
                attributeBinding = (AttributeBinding)ctrlBinding;
            }
        }
        return attributeBinding;
    }

    public static DCBindingContainer getBindings() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExpressionFactory exprfactory =
            fc.getApplication().getExpressionFactory();
        ELContext elctx = fc.getELContext();

        ValueExpression valueExpression =
            exprfactory.createValueExpression(elctx, "#{bindings}",
                                              Object.class);

        DCBindingContainer dcbinding =
            (DCBindingContainer)valueExpression.getValue(elctx);

        return dcbinding;
    }

    /*
     * Evaluates EL expression and returns value.
     */
    public static <T> T getValueFromELExpression(String expression,
                                                 Class<T> clazz) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        Application app = facesContext.getApplication();
        ExpressionFactory elFactory = app.getExpressionFactory();
        ELContext elContext = facesContext.getELContext();
        ValueExpression valueExp =
            elFactory.createValueExpression(elContext, expression, clazz);
        return (T)valueExp.getValue(elContext);
    }

    /*
     * Gets MethodExpression based on the EL expression. MethodExpression can then be used to invoke the method.
     */
    public static MethodExpression getMethodExpressionFromEL(String expression,
                                                             Class<?> returnType,
                                                             Class[] paramTypes) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        Application app = facesContext.getApplication();
        ExpressionFactory elFactory = app.getExpressionFactory();
        ELContext elContext = facesContext.getELContext();
        MethodExpression methodExp =
            elFactory.createMethodExpression(elContext, expression, returnType,
                                             paramTypes);
        return methodExp;
    }

    public static ELContext getELContext() {
        return FacesContext.getCurrentInstance().getELContext();
    }

    /*
     * Shows FacesMessage. The message will not be bound to any component.
     */
    public static void showFacesMessage(FacesMessage fm) {
        FacesContext.getCurrentInstance().addMessage(null, fm);
    }

    /*
     * Launch bounded taskFlow based on provided parameters.
     */
    public static void launchTaskFlow(String id, String taskFlowId,
                                      String name, String icon,
                                      String description, String helpTopicId,
                                      boolean inDialog,
                                      Map<String, Object> params) {
        // create JSON payload for the contextual event
        String jsonPayLoad =
            TaskFlowUtils.createContextualEventPayLoad(id, taskFlowId, name,
                                                       icon, description,
                                                       helpTopicId, inDialog,
                                                       params);

        // create and enqueue contextual event
        DCBindingContainer bc =
            (DCBindingContainer)BindingContext.getCurrent().getCurrentBindingsEntry();
        DCControlBinding ctrlBinding =
            bc.findCtrlBinding(TaskFlowUtils.RAISE_TASK_FLOW_LAUNCH_EVENT);
        // support both bindings - using eventBinding as well as methodAction
        if (ctrlBinding instanceof JUEventBinding) {
            JUEventBinding eventProducer = (JUEventBinding)ctrlBinding;
            bc.getEventDispatcher().queueEvent(eventProducer, jsonPayLoad);
        } else if (ctrlBinding instanceof JUCtrlActionBinding) {
            JUCtrlActionBinding actionBinding =
                (JUCtrlActionBinding)ctrlBinding;
            bc.getEventDispatcher().queueEvent(actionBinding.getEventProducer(),
                                               jsonPayLoad);
        } else {
            throw new IllegalArgumentException("Incorrect binding for " +
                                               TaskFlowUtils.RAISE_TASK_FLOW_LAUNCH_EVENT);
        }
        bc.getEventDispatcher().processContextualEvents();
    }

    /*
     * Redirect to a provided url.
     */
    public static void redirect(String url) {
        try {
            FacesContext fctx = FacesContext.getCurrentInstance();
            fctx.getExternalContext().redirect(url);
            fctx.responseComplete();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
