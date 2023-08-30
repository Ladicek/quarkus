package io.quarkus.arc.processor;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.invoke.Transformer;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;
import org.jboss.logging.Logger;
import org.objectweb.asm.Opcodes;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Invoker;
import io.quarkus.arc.impl.InvokerCleanupTasks;
import io.quarkus.arc.processor.ResourceOutput.Resource;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;

public class InvokerGenerator extends AbstractGenerator {
    private static final Logger LOGGER = Logger.getLogger(InvokerGenerator.class);

    private final Predicate<DotName> applicationClassPredicate;
    private final IndexView beanArchiveIndex;
    private final BeanDeployment beanDeployment;

    private final Assignability assignability;

    InvokerGenerator(boolean generateSources, Predicate<DotName> applicationClassPredicate, BeanDeployment deployment) {
        super(generateSources);
        this.applicationClassPredicate = applicationClassPredicate;
        this.beanArchiveIndex = deployment.getBeanArchiveIndex();
        this.beanDeployment = deployment;

        this.assignability = new Assignability(deployment.getBeanArchiveIndex());
    }

    Collection<Resource> generate(InvokerInfo invoker) {
        Function<String, Resource.SpecialType> specialTypeFunction = className -> {
            if (className.equals(invoker.className) || className.equals(invoker.wrapperClassName)) {
                return Resource.SpecialType.INVOKER;
            }
            return null;
        };

        ResourceClassOutput classOutput = new ResourceClassOutput(
                applicationClassPredicate.test(invoker.beanClass.name()), specialTypeFunction, generateSources);

        createInvokerClass(classOutput, invoker);
        createInvokerWrapperClass(classOutput, invoker);

        return classOutput.getResources();
    }

    // ---

    private void createInvokerWrapperClass(ClassOutput classOutput, InvokerInfo invoker) {
        if (invoker.wrapperClassName == null) {
            return;
        }

        try (ClassCreator clazz = ClassCreator.builder()
                .classOutput(classOutput)
                .className(invoker.wrapperClassName)
                .interfaces(Invoker.class)
                .build()) {

            FieldCreator instance = clazz.getFieldCreator("INSTANCE", Invoker.class)
                    .setModifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

            MethodCreator clinit = clazz.getMethodCreator(Methods.CLINIT, void.class)
                    .setModifiers(Opcodes.ACC_STATIC);
            clinit.writeStaticField(instance.getFieldDescriptor(),
                    clinit.newInstance(MethodDescriptor.ofConstructor(clazz.getClassName())));
            clinit.returnVoid();

            FieldCreator delegate = clazz.getFieldCreator("delegate", Invoker.class)
                    .setModifiers(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);

            MethodCreator ctor = clazz.getMethodCreator(Methods.INIT, void.class)
                    .setModifiers(Opcodes.ACC_PRIVATE);
            ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, Methods.INIT, void.class), ctor.getThis());
            ctor.writeInstanceField(delegate.getFieldDescriptor(), ctor.getThis(),
                    ctor.readStaticField(FieldDescriptor.of(invoker.className, "INSTANCE", Invoker.class)));
            ctor.returnVoid();

            MethodCreator invoke = clazz.getMethodCreator("invoke", Object.class, Object.class, Object[].class);
            ResultHandle targetInstance = invoke.getMethodParam(0);
            ResultHandle argumentsArray = invoke.getMethodParam(1);
            ResultHandle delegateInvoker = invoke.readInstanceField(delegate.getFieldDescriptor(), invoke.getThis());
            MethodInfo wrappingMethod = findWrapper(invoker);
            ResultHandle result = invoker.invocationWrapper.clazz.isInterface()
                    ? invoke.invokeStaticInterfaceMethod(wrappingMethod, targetInstance, argumentsArray, delegateInvoker)
                    : invoke.invokeStaticMethod(wrappingMethod, targetInstance, argumentsArray, delegateInvoker);
            if (wrappingMethod.returnType().kind() == Type.Kind.VOID) {
                result = invoke.loadNull();
            }
            invoke.returnValue(result);

            LOGGER.debugf("InvokerWrapper class generated: %s", clazz.getClassName());
        }
    }

    private MethodInfo findWrapper(InvokerInfo invoker) {
        InvocationTransformer wrapper = invoker.invocationWrapper;
        ClassInfo clazz = beanArchiveIndex.getClassByName(wrapper.clazz);
        List<MethodInfo> methods = new ArrayList<>();
        for (MethodInfo method : clazz.methods()) {
            if (Modifier.isStatic(method.flags()) && wrapper.method.equals(method.name())) {
                methods.add(method);
            }
        }

        List<MethodInfo> matching = new ArrayList<>();
        List<MethodInfo> notMatching = new ArrayList<>();
        for (MethodInfo method : methods) {
            if (method.parametersCount() == 3
                    && method.parameterType(1).kind() == Type.Kind.ARRAY
                    && method.parameterType(1).asArrayType().dimensions() == 1
                    && method.parameterType(1).asArrayType().component().name().equals(DotName.OBJECT_NAME)
                    && method.parameterType(2).name().equals(DotName.createSimple(Invoker.class))) {

                Type targetInstanceType = method.parameterType(0);
                boolean targetInstanceOk = isAnyType(targetInstanceType)
                        || assignability.isSupertype(targetInstanceType, ClassType.create(invoker.beanClass.name()));

                boolean isInvokerRaw = method.parameterType(2).kind() == Type.Kind.CLASS;
                boolean isInvokerParameterized = method.parameterType(2).kind() == Type.Kind.PARAMETERIZED_TYPE
                        && method.parameterType(2).asParameterizedType().arguments().size() == 2;
                boolean invokerTargetInstanceOk = isInvokerRaw
                        || isInvokerParameterized
                                && targetInstanceType.equals(method.parameterType(2).asParameterizedType().arguments().get(0));

                if (targetInstanceOk && invokerTargetInstanceOk) {
                    matching.add(method);
                } else {
                    notMatching.add(method);
                }
            } else {
                notMatching.add(method);
            }
        }

        if (matching.size() == 1) {
            return matching.get(0);
        }

        if (matching.isEmpty()) {
            String expectation = ""
                    + "\tmatching methods must be `static` and take 3 parameters (instance, argument array, invoker)\n"
                    + "\tthe 1st parameter must be a supertype of " + invoker.beanClass.name() + ", possibly Object\n"
                    + "\tthe 2nd parameter must be Object[]\n"
                    + "\tthe 3rd parameter must be Invoker<type of 1st parameter, some type>";
            if (notMatching.isEmpty()) {
                throw new IllegalArgumentException(""
                        + "Error creating invoker for method " + invoker + ":\n"
                        + "\tno matching method found for " + wrapper + "\n"
                        + expectation);
            } else {
                throw new IllegalArgumentException(""
                        + "Error creating invoker for method " + invoker + ":\n"
                        + "\tno matching method found for " + wrapper + "\n"
                        + "\tfound methods that do not match:\n"
                        + notMatching.stream().map(it -> "\t- " + it).collect(Collectors.joining("\n")) + "\n"
                        + expectation);
            }
        } else {
            throw new IllegalArgumentException(""
                    + "Error creating invoker for method " + invoker + ":\n"
                    + "\ttoo many matching methods for " + wrapper + ":\n"
                    + matching.stream().map(it -> "\t- " + it).collect(Collectors.joining("\n")));
        }
    }

    // ---

    private void createInvokerClass(ClassOutput classOutput, InvokerInfo invoker) {
        MethodInfo targetMethod = invoker.method;

/*
        // Ljava/lang/Object;Lio/quarkus/arc/Invoker<Lcom/example/MyClass;Ljava/lang/String;>;
        String signature = String.format("L%s;L%s<L%s;L%s;>;",
                Object.class.getName().replace('.', '/'),
                Invoker.class.getName().replace('.', '/'),
                invokableMethod.getBeanClass().name().toString().replace('.', '/'),
                invokableMethod.getMethod().returnType().name().toString().replace('.', '/'));
*/

        // TODO handle return type of void or primitive (and possibly even parameterized etc.)
        try (ClassCreator clazz = ClassCreator.builder()
                .classOutput(classOutput)
                .className(invoker.className)
                .interfaces(Invoker.class)
/*
                .signature(signature)
*/
                .build()) {

            FieldCreator instance = clazz.getFieldCreator("INSTANCE", Invoker.class)
                    .setModifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

            FieldCreator returnValueTransformerInstance = null;
            if (invoker.returnValueTransformer != null && invoker.returnValueTransformer.method == null) {
                // transformer class approach
                returnValueTransformerInstance = clazz.getFieldCreator("returnValueTransformer", Transformer.class)
                        .setModifiers(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
            }

            MethodCreator clinit = clazz.getMethodCreator(Methods.CLINIT, void.class)
                    .setModifiers(Opcodes.ACC_STATIC);
            clinit.writeStaticField(instance.getFieldDescriptor(),
                    clinit.newInstance(MethodDescriptor.ofConstructor(clazz.getClassName())));
            clinit.returnVoid();

            MethodCreator ctor = clazz.getMethodCreator(Methods.INIT, void.class)
                    .setModifiers(Opcodes.ACC_PRIVATE);
            ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, Methods.INIT, void.class), ctor.getThis());
            if (returnValueTransformerInstance != null) {
                ctor.writeInstanceField(returnValueTransformerInstance.getFieldDescriptor(), ctor.getThis(),
                        ctor.newInstance(MethodDescriptor.ofConstructor(invoker.returnValueTransformer.clazz)));
            }
            ctor.returnVoid();

/*
            MethodCreator invoke = clazz.getMethodCreator("invoke",
                    invokableMethod.getMethod().returnType().name().toString(),
                    invokableMethod.getBeanClass().name().toString(), Object[].class);
*/
            MethodCreator invoke = clazz.getMethodCreator("invoke", Object.class, Object.class, Object[].class);

            FinisherGenerator finisher = new FinisherGenerator(invoke);
            LookupGenerator lookup = prepareLookup(invoke, invoker);

            ResultHandle targetInstance = null;
            if (!Modifier.isStatic(targetMethod.flags())) {
                Type instanceType = ClassType.create(invoker.beanClass.name());
                targetInstance = invoker.instanceLookup
                        ? performLookup(-1, invoke, finisher, lookup)
                        : invoke.getMethodParam(0);
                targetInstance = findAndInvokeTransformer(invoker.instanceTransfomer, instanceType,
                        invoker, targetInstance, invoke, finisher);
            }

            ResultHandle argumentsArray = invoke.getMethodParam(1);
            ResultHandle[] unfoldedArguments = new ResultHandle[targetMethod.parametersCount()];
            for (int i = 0; i < targetMethod.parametersCount(); i++) {
                Type parameterType = targetMethod.parameterType(i);
                ResultHandle originalArgument = invoker.argumentLookups[i]
                        ? performLookup(i, invoke, finisher, lookup)
                        : invoke.readArrayValue(argumentsArray, i);
                unfoldedArguments[i] = findAndInvokeTransformer(invoker.argumentTransformers[i], parameterType,
                        invoker, originalArgument, invoke, finisher);
            }

            BytecodeCreator bytecode = invoke;
            if (invoker.exceptionTransformer != null || finisher.wasCreated()) {
                // TODO Exception instead of Throwable? here (transformer expected type) and in the catch block below
                Type exceptionType = ClassType.create(DotName.createSimple(Throwable.class));
                TryBlock tryBlock = invoke.tryBlock();
                CatchBlockCreator catchBlock = tryBlock.addCatch(Throwable.class);

                if (finisher.wasCreated()) {
                    catchBlock.invokeVirtualMethod(MethodDescriptor.ofMethod(InvokerCleanupTasks.class, "finish", void.class),
                            finisher.getOrCreate());
                }

                if (invoker.exceptionTransformer != null) {
                    catchBlock.returnValue(
                            findAndInvokeTransformer(invoker.exceptionTransformer, exceptionType,
                                    invoker, catchBlock.getCaughtException(), catchBlock, null));
                } else {
                    catchBlock.throwException(catchBlock.getCaughtException());
                }

                bytecode = tryBlock;
            }

            ResultHandle result;
            if (Modifier.isStatic(targetMethod.flags())) {
                result = Modifier.isInterface(invoker.beanClass.flags())
                        ? bytecode.invokeStaticInterfaceMethod(targetMethod, unfoldedArguments)
                        : bytecode.invokeStaticMethod(targetMethod, unfoldedArguments);
            } else {
                result = Modifier.isInterface(invoker.beanClass.flags())
                        ? bytecode.invokeInterfaceMethod(targetMethod, targetInstance, unfoldedArguments)
                        : bytecode.invokeVirtualMethod(targetMethod, targetInstance, unfoldedArguments);
            }
            if (targetMethod.returnType().kind() == Type.Kind.VOID) {
                result = bytecode.loadNull();
            }
            if (returnValueTransformerInstance != null) {
                MethodDescriptor tform = MethodDescriptor.ofMethod(Transformer.class, "transform", Object.class, Object.class);
                ResultHandle tformer = bytecode.readInstanceField(returnValueTransformerInstance.getFieldDescriptor(),
                        bytecode.getThis());
                result = bytecode.invokeInterfaceMethod(tform, tformer, result);
            } else {
                Type returnValueType = targetMethod.returnType();
                result = findAndInvokeTransformer(invoker.returnValueTransformer, returnValueType,
                        invoker, result, bytecode, null);
            }
            if (finisher.wasCreated()) {
                bytecode.invokeVirtualMethod(MethodDescriptor.ofMethod(InvokerCleanupTasks.class, "finish", void.class),
                        finisher.getOrCreate());
            }
            bytecode.returnValue(result);

/*
            MethodCreator invokeBridge = clazz.getMethodCreator("invoke", Object.class, Object.class, Object[].class);
            invokeBridge.setModifiers(0x00000040 | 0x00001000); // bridge + synthetic
            invokeBridge.returnValue(invokeBridge.invokeVirtualMethod(invoke.getMethodDescriptor(),
                    invokeBridge.getThis(), invokeBridge.getMethodParam(0), invokeBridge.getMethodParam(1)));
*/

            LOGGER.debugf("Invoker class generated: %s", clazz.getClassName());
        }
    }

    static class FinisherGenerator {
        private final MethodCreator method;
        private ResultHandle finisher;

        FinisherGenerator(MethodCreator method) {
            this.method = method;
        }

        ResultHandle getOrCreate() {
            if (finisher == null) {
                finisher = method.newInstance(MethodDescriptor.ofConstructor(InvokerCleanupTasks.class));
            }
            return finisher;
        }

        boolean wasCreated() {
            return finisher != null;
        }
    }

    static class LookupGenerator {
        private final BeanInfo targetInstanceBeanInfo;
        private final BeanInfo[] argumentBeanInfos;

        private final MethodCreator method;

        private ResultHandle arc;
        private ResultHandle targetInstanceBean;
        private ResultHandle[] argumentBeans;

        LookupGenerator(BeanInfo targetInstanceBeanInfo, BeanInfo[] argumentBeanInfos, MethodCreator method) {
            this.targetInstanceBeanInfo = targetInstanceBeanInfo;
            this.argumentBeanInfos = argumentBeanInfos;

            this.method = method;

            this.argumentBeans = new ResultHandle[argumentBeanInfos.length];
        }

        ResultHandle arc() {
            if (arc == null) {
                arc = method.invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));
            }
            return arc;
        }

        ResultHandle targetInstanceBean() {
            if (targetInstanceBean == null) {
                assert targetInstanceBeanInfo != null;

                targetInstanceBean = method.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(ArcContainer.class, "bean", InjectableBean.class, String.class),
                        arc(), method.load(targetInstanceBeanInfo.getIdentifier()));
            }
            return targetInstanceBean;
        }

        ResultHandle argumentBean(int position) {
            if (argumentBeans[position] == null) {
                assert argumentBeanInfos[position] != null;

                argumentBeans[position] = method.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(ArcContainer.class, "bean", InjectableBean.class, String.class),
                        arc(), method.load(argumentBeanInfos[position].getIdentifier()));

            }
            return argumentBeans[position];
        }
    }

    private LookupGenerator prepareLookup(MethodCreator method, InvokerInfo invoker) {
        boolean lookupUsed = invoker.instanceLookup;
        for (boolean argumentLookup : invoker.argumentLookups) {
            lookupUsed |= argumentLookup;
        }

        if (!lookupUsed) {
            return null;
        }

        BeanInfo targetInstance = null;
        if (invoker.instanceLookup) {
            targetInstance = resolveBean(invoker, null);
        }

        BeanInfo[] arguments = new BeanInfo[invoker.argumentLookups.length];
        for (int i = 0; i < invoker.argumentLookups.length; i++) {
            arguments[i] = invoker.argumentLookups[i]
                    ? resolveBean(invoker, invoker.method.parameters().get(i))
                    : null;
        }

        return new LookupGenerator(targetInstance, arguments, method);
    }

    private BeanInfo resolveBean(InvokerInfo invoker, MethodParameterInfo parameter) {
        Type beanType;
        Set<AnnotationInstance> qualifiers;
        if (parameter == null) {
            beanType = ClassType.create(invoker.beanClass.name());
            // TODO what's the right way to obtain qualifiers here?
            qualifiers = new HashSet<>();
            beanDeployment.getAnnotations(invoker.beanClass)
                    .stream()
                    .filter(it -> it.target().kind() == AnnotationTarget.Kind.CLASS)
                    .forEach(it -> qualifiers.addAll(beanDeployment.extractQualifiers(it)));
        } else {
            beanType = parameter.type();
            qualifiers = new HashSet<>();
            beanDeployment.getAnnotations(parameter.method())
                    .stream()
                    .filter(it -> it.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                            && it.target().asMethodParameter().position() == parameter.position())
                    .forEach(it -> qualifiers.addAll(beanDeployment.extractQualifiers(it)));
        }
        Set<BeanInfo> beans = beanDeployment.getBeanResolver().resolveBeans(beanType, qualifiers);
        if (beans.isEmpty()) {
            throw new DeploymentException(new UnsatisfiedResolutionException("Unsatisfied dependency for type " + beanType
                    + " and qualifiers " + qualifiers + " when resolving "
                    + (parameter == null ? "target instance" : "argument #" + parameter.position())
                    + " of invokable method " + invoker));
        }
        try {
            return beanDeployment.getBeanResolver().resolveAmbiguity(beans);
        } catch (Exception e) {
            throw new DeploymentException(new AmbiguousResolutionException("Ambiguous dependencies for type " + beanType
                    + " and qualifiers " + qualifiers + " when resolving "
                    + (parameter == null ? "target instance" : "argument #" + parameter.position())
                    + " of invokable method " + invoker + ":\n"
                    + beans.stream().map(it -> "\t- " + it).collect(Collectors.joining("\n"))));
        }
    }

    private ResultHandle performLookup(int parameterPos, MethodCreator bytecode,
            FinisherGenerator finisher, LookupGenerator lookup) {

        ResultHandle arc = lookup.arc();

        ResultHandle bean;
        boolean needsFinish = false;
        if (parameterPos < 0) {
            // target instance
            bean = lookup.targetInstanceBean();
            if (BuiltinScope.DEPENDENT.is(lookup.targetInstanceBeanInfo.getScope())) {
                needsFinish = true;
            }
        } else {
            bean = lookup.argumentBean(parameterPos);
            if (BuiltinScope.DEPENDENT.is(lookup.argumentBeanInfos[parameterPos].getScope())) {
                needsFinish = true;
            }
        }
        ResultHandle instanceHandle = bytecode.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, InjectableBean.class),
                arc, bean);
        ResultHandle instance = bytecode.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class), instanceHandle);
        if (needsFinish) {
            bytecode.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(InvokerCleanupTasks.class, "addBean", void.class, InstanceHandle.class),
                    finisher.getOrCreate(), instanceHandle);
        }
        return instance;
    }

    private ResultHandle findAndInvokeTransformer(InvocationTransformer transformer, Type expectedType,
            InvokerInfo invoker, ResultHandle originalValue, BytecodeCreator bytecode, FinisherGenerator finisher) {
        if (transformer == null) {
            return originalValue;
        }

        CandidateMethods candidates = findCandidates(transformer, expectedType, invoker);
        CandidateMethod transformerMethod = candidates.resolve();
        MethodDescriptor transformerMethodDescriptor = MethodDescriptor.of(transformerMethod.method);

        if (Modifier.isStatic(transformerMethod.method.flags())) {
            ResultHandle[] arguments = new ResultHandle[transformerMethod.usesFinisher() ? 2 : 1];
            arguments[0] = originalValue;
            if (transformerMethod.usesFinisher()) {
                arguments[1] = finisher.getOrCreate();
            }

            if (transformer.clazz.isInterface()) {
                return bytecode.invokeStaticInterfaceMethod(transformerMethodDescriptor, arguments);
            } else {
                return bytecode.invokeStaticMethod(transformerMethodDescriptor, arguments);
            }
        } else {
            if (transformer.clazz.isInterface()) {
                return bytecode.invokeInterfaceMethod(transformerMethodDescriptor, originalValue);
            } else {
                return bytecode.invokeVirtualMethod(transformerMethodDescriptor, originalValue);
            }
        }
    }

    private CandidateMethods findCandidates(InvocationTransformer transformer, Type expectedType, InvokerInfo invoker) {
        assert transformer.kind != InvocationTransformerKind.WRAPPER;

        ClassInfo clazz = beanArchiveIndex.getClassByName(transformer.clazz);

        // static methods only from the given class
        // instance methods also from superclasses and superinterfaces

        // first, set up the work queue so that it contains the given class and all its superclasses
        // next, as each class from the queue is processed, add its interfaces to the queue
        // this is so that superclasses are processed before interfaces
        Deque<ClassInfo> workQueue = new ArrayDeque<>();
        while (clazz != null) {
            workQueue.addLast(clazz);
            clazz = clazz.superName() == null ? null : beanArchiveIndex.getClassByName(clazz.superName());
        }

        boolean originalClass = true;
        Set<Methods.MethodKey> seenMethods = new HashSet<>();
        while (!workQueue.isEmpty()) {
            ClassInfo current = workQueue.removeFirst();

            for (MethodInfo method : current.methods()) {
                if (!transformer.method.equals(method.name())) {
                    continue;
                }

                Methods.MethodKey key = new Methods.MethodKey(method);

                if (Modifier.isStatic(method.flags()) && originalClass) {
                    seenMethods.add(key);
                } else {
                    if (!Methods.isOverriden(key, seenMethods)) {
                        seenMethods.add(key);
                    }
                }
            }

            for (DotName iface : current.interfaceNames()) {
                workQueue.addLast(beanArchiveIndex.getClassByName(iface));
            }

            originalClass = false;
        }

        List<CandidateMethod> matching = new ArrayList<>();
        List<CandidateMethod> notMatching = new ArrayList<>();
        for (Methods.MethodKey seenMethod : seenMethods) {
            CandidateMethod candidate = new CandidateMethod(seenMethod.method, assignability);
            if (candidate.matches(transformer, expectedType)) {
                matching.add(candidate);
            } else {
                notMatching.add(candidate);
            }
        }
        return new CandidateMethods(transformer, expectedType, matching, notMatching, invoker);
    }

    static class CandidateMethods {
        // most of the fields here are only used for providing a good error message
        final InvocationTransformer transformer;
        final Type expectedType;
        final List<CandidateMethod> matching;
        final List<CandidateMethod> notMatching;
        final InvokerInfo invoker;

        CandidateMethods(InvocationTransformer transformer, Type expectedType,
                List<CandidateMethod> matching, List<CandidateMethod> notMatching,
                InvokerInfo invoker) {
            this.transformer = transformer;
            this.expectedType = expectedType;
            this.matching = matching;
            this.notMatching = notMatching;
            this.invoker = invoker;
        }

        CandidateMethod resolve() {
            if (matching.size() == 1) {
                return matching.get(0);
            }

            if (matching.isEmpty()) {
                String expectedType = this.expectedType.toString();
                String expectation = "";
                if (transformer.isInputTransformer()) {
                    expectation = "\n"
                            + "\tmatching `static` methods must take 1 or 2 parameters and return " + expectedType
                            + " (or subtype)\n"
                            + "\t(if the `static` method takes 2 parameters, the 2nd must be `Consumer<Runnable>`)\n"
                            + "\tmatching instance methods must take no parameter and return " + expectedType + " (or subtype)";
                } else if (transformer.isOutputTransformer()) {
                    expectation = "\n"
                            + "\tmatching `static` method must take 1 parameter of type " + expectedType + " (or supertype)\n"
                            + "\tmatching instance methods must be declared on " + expectedType
                            + " (or supertype) and take no parameter";
                }

                if (notMatching.isEmpty()) {
                    throw new IllegalArgumentException(""
                            + "Error creating invoker for method " + invoker + ":\n"
                            + "\tno matching method found for " + transformer
                            + expectation);
                } else {
                    throw new IllegalArgumentException(""
                            + "Error creating invoker for method " + invoker + ":\n"
                            + "\tno matching method found for " + transformer + "\n"
                            + "\tfound methods that do not match:\n"
                            + notMatching.stream().map(it -> "\t- " + it).collect(Collectors.joining("\n"))
                            + expectation);
                }
            } else {
                throw new IllegalArgumentException(""
                        + "Error creating invoker for method " + invoker + ":\n"
                        + "\ttoo many matching methods for " + transformer + ":\n"
                        + matching.stream().map(it -> "\t- " + it).collect(Collectors.joining("\n")));
            }
        }
    }

    static class CandidateMethod {
        final MethodInfo method;
        final Assignability assignability;

        CandidateMethod(MethodInfo method, Assignability assignability) {
            this.method = method;
            this.assignability = assignability;
        }

        boolean matches(InvocationTransformer transformer, Type expectedType) {
            if (transformer.isInputTransformer()) {
                // for input transformer (target instance, argument):
                // - we can't check what comes into the transformer
                // - we can check what comes out of the transformer, because that's what the invokable method consumes
                //   (and the transformer must produce a subtype)

                boolean returnTypeOk = isAnyType(method.returnType()) || isSubtype(method.returnType(), expectedType);
                if (Modifier.isStatic(method.flags())) {
                    return method.parametersCount() == 1 && returnTypeOk
                            || method.parametersCount() == 2 && returnTypeOk && isFinisher(method.parameterType(1));
                } else {
                    return method.parametersCount() == 0 && returnTypeOk;
                }
            } else if (transformer.isOutputTransformer()) {
                // for output transformer (return value, exception):
                // - we can check what comes into the transformer, because that's what the invokable method produces
                //   (and the transformer must consume a supertype)
                // - we can't check what comes out of the transformer

                if (Modifier.isStatic(method.flags())) {
                    return method.parametersCount() == 1
                            && (isAnyType(method.parameterType(0)) || isSupertype(method.parameterType(0), expectedType));
                } else {
                    return method.parametersCount() == 0
                            && isSupertype(ClassType.create(method.declaringClass().name()), expectedType);
                }
            } else {
                throw new IllegalArgumentException(transformer.toString());
            }
        }

        // if `matches()` returns `false`, there's no point in calling this method
        boolean usesFinisher() {
            return Modifier.isStatic(method.flags())
                    && method.parametersCount() == 2
                    && isFinisher(method.parameterType(1));
        }

        private boolean isFinisher(Type type) {
            if (type.kind() == Type.Kind.CLASS) {
                return type.name().equals(DotName.createSimple(Consumer.class));
            } else if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                return type.name().equals(DotName.createSimple(Consumer.class))
                        && type.asParameterizedType().arguments().size() == 1
                        && type.asParameterizedType().arguments().get(0).kind() == Type.Kind.CLASS
                        && type.asParameterizedType().arguments().get(0).name().equals(DotName.createSimple(Runnable.class));
            } else {
                return false;
            }
        }

        private boolean isSubtype(Type a, Type b) {
            return assignability.isSubtype(a, b);
        }

        private boolean isSupertype(Type a, Type b) {
            return assignability.isSupertype(a, b);
        }

        @Override
        public String toString() {
            return method.toString() + " declared on " + method.declaringClass();
        }
    }

    // ---

    static boolean isAnyType(Type t) {
        if (ClassType.OBJECT_TYPE.equals(t)) {
            return true;
        }
        if (t.kind() == Type.Kind.TYPE_VARIABLE) {
            TypeVariable typeVar = t.asTypeVariable();
            if (typeVar.bounds().isEmpty()) {
                return true;
            }
            if (isAnyType(typeVar.bounds().get(0))) {
                return true;
            }
        }
        return false;
    }

    // TODO this is just a prototype, need proper specification first
    //  the CDI bean assignability rules are likely not comprehensive enough
    //  not sure about reusing JLS subtyping rules
    static class Assignability {
        private final IndexView index;

        Assignability(IndexView index) {
            this.index = index;
        }

        boolean isSubtype(Type a, Type b) {
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);

            switch (a.kind()) {
                case VOID:
                    // TODO maybe treat as a bottom type? (void method invokers return `null`)
                    return b.kind() == Type.Kind.VOID
                            || b.kind() == Type.Kind.CLASS && b.asClassType().name().equals(DotName.createSimple(Void.class));
                case PRIMITIVE:
                    // TODO maybe consider wider primitive types as supertypes, like the JLS?
                    // TODO consider wrapper types
                    return b.kind() == Type.Kind.PRIMITIVE
                            && a.asPrimitiveType().primitive() == b.asPrimitiveType().primitive();
                case ARRAY:
                    return b.kind() == Type.Kind.ARRAY
                            && arrayTotalDimensions(a) == arrayTotalDimensions(b)
                            && isSubtype(arrayElementType(a), arrayElementType(b));
                case CLASS:
                    // TODO see above for void and primitive
                    if (b.kind() == Type.Kind.VOID) {
                        return a.asClassType().name().equals(DotName.createSimple(Void.class));
                    } else if (b.kind() == Type.Kind.PRIMITIVE) {
                        return false;
                    } else if (b.kind() == Type.Kind.ARRAY) {
                        return false;
                    } else if (b.kind() == Type.Kind.CLASS) {
                        return isClassSubtype(a.asClassType(), b.asClassType());
                    } else if (b.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                        return isClassSubtype(a.asClassType(), ClassType.create(b.name()));
                    } else if (b.kind() == Type.Kind.TYPE_VARIABLE) {
                        Type firstBound = b.asTypeVariable().bounds().isEmpty()
                                ? ClassType.OBJECT_TYPE
                                : b.asTypeVariable().bounds().get(0);
                        return isSubtype(a, firstBound);
                    }
                case PARAMETERIZED_TYPE:
                    // TODO see above for void and primitive
                    if (b.kind() == Type.Kind.VOID || b.kind() == Type.Kind.PRIMITIVE || b.kind() == Type.Kind.ARRAY) {
                        return false;
                    } else if (b.kind() == Type.Kind.CLASS) {
                        return isClassSubtype((ClassType) Type.create(a.name(), Type.Kind.CLASS), b.asClassType());
                    } else if (b.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                        return isClassSubtype(ClassType.create(a.name()), ClassType.create(b.name()));
                    }
                case TYPE_VARIABLE:
                    // TODO see above for void and primitive
                    if (b.kind() == Type.Kind.VOID || b.kind() == Type.Kind.PRIMITIVE || b.kind() == Type.Kind.ARRAY) {
                        return false;
                    } else if (b.kind() == Type.Kind.CLASS) {
                        Type firstBound = a.asTypeVariable().bounds().isEmpty()
                                ? ClassType.OBJECT_TYPE
                                : a.asTypeVariable().bounds().get(0);
                        return isSubtype(firstBound, b);
                    }
                case WILDCARD_TYPE:
                default:
                    throw new IllegalArgumentException("Cannot determine assignability between " + a + " and " + b);
            }
        }

        boolean isSupertype(Type a, Type b) {
            return isSubtype(b, a);
        }

        private int arrayTotalDimensions(Type type) {
            int dimensions = 0;
            while (type.kind() == Type.Kind.ARRAY) {
                dimensions += type.asArrayType().dimensions();
                type = type.asArrayType().component();
            }
            return dimensions;
        }

        private Type arrayElementType(Type type) {
            while (type.kind() == Type.Kind.ARRAY) {
                type = type.asArrayType().component();
            }
            return type;
        }

        private boolean isClassSubtype(ClassType a, ClassType b) {
            while (a != null) {
                if (a.name().equals(b.name())) {
                    return true;
                }

                ClassInfo aClazz = index.getClassByName(a.name());
                assert aClazz != null;
                a = (ClassType) aClazz.superClassType();
            }
            return false;
        }
    }
}
