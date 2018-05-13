package processing.mode.android.debugger;

import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;

import java.io.IOException;
import java.util.List;

public class FieldMonitor {

    public static final String CLASS_NAME = "Test";
    public static final String FIELD_NAME = "foo";

    public static void main(String[] args)
            throws IOException, InterruptedException {
        // connect
        VirtualMachine vm = new VMAcquirer().connect(8000);

        // set watch field on already loaded classes
        List<ReferenceType> referenceTypes = vm.classesByName(CLASS_NAME);
        for (ReferenceType refType : referenceTypes) {
            addFieldWatch(vm, refType);
        }
        // watch for loaded classes
        addClassWatch(vm);

        // resume the vm
        vm.resume();

        // process events
        EventQueue eventQueue = vm.eventQueue();
        while (true) {
            EventSet eventSet = eventQueue.remove();
            for (Event event : eventSet) {
                if (event instanceof VMDeathEvent
                        || event instanceof VMDisconnectEvent) {
                    // exit
                    return;
                } else if (event instanceof ClassPrepareEvent) {
                    // watch field on loaded class
                    ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
                    ReferenceType refType = classPrepEvent
                            .referenceType();
                    addFieldWatch(vm, refType);
                } else if (event instanceof ModificationWatchpointEvent) {
                    // a Test.foo has changed
                    ModificationWatchpointEvent modEvent = (ModificationWatchpointEvent) event;
                    System.out.println("old="
                            + modEvent.valueCurrent());
                    System.out.println("new=" + modEvent.valueToBe());
                    System.out.println();
                }
            }
            eventSet.resume();
        }
    }

    /** Watch all classes of name "Test" */
    private static void addClassWatch(VirtualMachine vm) {
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
        classPrepareRequest.addClassFilter(CLASS_NAME);
        classPrepareRequest.setEnabled(true);
    }

    /** Watch field of name "foo" */
    private static void addFieldWatch(VirtualMachine vm,
                                      ReferenceType refType) {
        EventRequestManager erm = vm.eventRequestManager();
        Field field = refType.fieldByName(FIELD_NAME);
        ModificationWatchpointRequest modificationWatchpointRequest = erm.createModificationWatchpointRequest(field);
        modificationWatchpointRequest.setEnabled(true);
    }

}
