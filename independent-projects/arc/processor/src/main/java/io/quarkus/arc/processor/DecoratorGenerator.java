package io.quarkus.arc.processor;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import io.quarkus.arc.InjectableDecorator;
import io.quarkus.arc.processor.BeanProcessor.PrivateMembersCollector;
import io.quarkus.arc.processor.ResourceOutput.Resource;
import io.quarkus.arc.processor.ResourceOutput.Resource.SpecialType;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 *
 * @author Martin Kouba
 */
public class DecoratorGenerator extends BeanGenerator {

    protected static final String FIELD_NAME_DECORATED_TYPES = "decoratedTypes";
    protected static final String FIELD_NAME_DELEGATE_TYPE = "delegateType";

    public DecoratorGenerator(AnnotationLiteralProcessor annotationLiterals, Predicate<DotName> applicationClassPredicate,
            PrivateMembersCollector privateMembers, boolean generateSources, ReflectionRegistration reflectionRegistration,
            Set<String> existingClasses, Map<BeanInfo, String> beanToGeneratedName,
            Predicate<DotName> injectionPointAnnotationsPredicate) {
        super(annotationLiterals, applicationClassPredicate, privateMembers, generateSources, reflectionRegistration,
                existingClasses, beanToGeneratedName, injectionPointAnnotationsPredicate);
    }

    /**
     *
     * @param decorator bean
     * @return a collection of resources
     */
    Collection<Resource> generate(DecoratorInfo decorator) {

        ProviderType providerType = new ProviderType(decorator.getProviderType());
        ClassInfo decoratorClass = decorator.getTarget().get().asClass();
        String baseName;
        if (decoratorClass.enclosingClass() != null) {
            baseName = DotNames.simpleName(decoratorClass.enclosingClass()) + "_" + DotNames.simpleName(decoratorClass);
        } else {
            baseName = DotNames.simpleName(decoratorClass);
        }
        String targetPackage = DotNames.packageName(providerType.name());
        String generatedName = generatedNameFromTarget(targetPackage, baseName, BEAN_SUFFIX);
        beanToGeneratedName.put(decorator, generatedName);
        if (existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(decorator.getBeanClass());
        ResourceClassOutput classOutput = new ResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.DECORATOR_BEAN : null, generateSources);

        // MyDecorator_Bean implements InjectableDecorator<T>
        ClassCreator decoratorCreator = ClassCreator.builder().classOutput(classOutput).className(generatedName)
                .interfaces(InjectableDecorator.class, Supplier.class)
                .build();

        // Fields
        FieldCreator beanTypes = decoratorCreator.getFieldCreator(FIELD_NAME_BEAN_TYPES, Set.class)
                .setModifiers(ACC_PRIVATE | ACC_FINAL);
        FieldCreator decoratedTypes = decoratorCreator.getFieldCreator(FIELD_NAME_DECORATED_TYPES, Set.class)
                .setModifiers(ACC_PRIVATE | ACC_FINAL);
        InjectionPointInfo delegateInjectionPoint = decorator.getDelegateInjectionPoint();
        FieldCreator delegateType = decoratorCreator.getFieldCreator(FIELD_NAME_DELEGATE_TYPE, Type.class)
                .setModifiers(ACC_PRIVATE | ACC_FINAL);
        FieldCreator delegateQualifiers = null;
        if (!delegateInjectionPoint.hasDefaultedQualifier()) {
            delegateQualifiers = decoratorCreator.getFieldCreator(FIELD_NAME_QUALIFIERS, Set.class)
                    .setModifiers(ACC_PRIVATE | ACC_FINAL);
        }

        Map<InjectionPointInfo, String> injectionPointToProviderField = new HashMap<>();
        initMaps(decorator, injectionPointToProviderField, Collections.emptyMap(), Collections.emptyMap());
        createProviderFields(decoratorCreator, decorator, injectionPointToProviderField, Collections.emptyMap(),
                Collections.emptyMap());
        createConstructor(classOutput, decoratorCreator, decorator, baseName, injectionPointToProviderField,
                delegateType, delegateQualifiers, decoratedTypes, reflectionRegistration);

        implementGetIdentifier(decorator, decoratorCreator);
        implementSupplierGet(decoratorCreator);
        implementCreate(classOutput, decoratorCreator, decorator, providerType, baseName,
                injectionPointToProviderField,
                Collections.emptyMap(), Collections.emptyMap(),
                reflectionRegistration, targetPackage, isApplicationClass);
        implementGet(decorator, decoratorCreator, providerType, baseName);
        implementGetTypes(decoratorCreator, beanTypes.getFieldDescriptor());
        implementGetBeanClass(decorator, decoratorCreator);
        // Decorators are always @Dependent and have always default qualifiers

        // InjectableDecorator methods
        implementGetDecoratedTypes(decoratorCreator, decoratedTypes.getFieldDescriptor());
        implementGetDelegateType(decoratorCreator, delegateType.getFieldDescriptor());
        implementGetDelegateQualifiers(decoratorCreator, delegateQualifiers);
        implementGetPriority(decoratorCreator, decorator);

        decoratorCreator.close();
        return classOutput.getResources();

    }

    protected void createConstructor(ClassOutput classOutput, ClassCreator creator, DecoratorInfo decorator,
            String baseName,
            Map<InjectionPointInfo, String> injectionPointToProviderField,
            FieldCreator delegateType,
            FieldCreator delegateQualifiers,
            FieldCreator decoratedTypes,
            ReflectionRegistration reflectionRegistration) {

        MethodCreator constructor = initConstructor(classOutput, creator, decorator, baseName, injectionPointToProviderField,
                Collections.emptyMap(), Collections.emptyMap(), annotationLiterals, reflectionRegistration);

        if (delegateQualifiers != null) {
            // delegateQualifiers = new HashSet<>()
            ResultHandle delegateQualifiersHandle = constructor.newInstance(MethodDescriptor.ofConstructor(HashSet.class));
            for (AnnotationInstance delegateQualifier : decorator.getDelegateQualifiers()) {
                // Create annotation literal first
                ClassInfo delegateQualifierClass = decorator.getDeployment().getInterceptorBinding(delegateQualifier.name());
                constructor.invokeInterfaceMethod(MethodDescriptors.SET_ADD, delegateQualifiersHandle,
                        annotationLiterals.process(constructor, classOutput, delegateQualifierClass, delegateQualifier,
                                Types.getPackageName(creator.getClassName())));
            }
            constructor.writeInstanceField(delegateQualifiers.getFieldDescriptor(), constructor.getThis(),
                    delegateQualifiersHandle);
        }

        // decoratedTypes = new HashSet<>()
        ResultHandle decoratedTypesHandle = constructor.newInstance(MethodDescriptor.ofConstructor(HashSet.class));
        // Get the TCCL - we will use it later
        ResultHandle currentThread = constructor
                .invokeStaticMethod(MethodDescriptors.THREAD_CURRENT_THREAD);
        ResultHandle tccl = constructor.invokeVirtualMethod(MethodDescriptors.THREAD_GET_TCCL, currentThread);
        for (org.jboss.jandex.Type decoratedType : decorator.getDecoratedTypes()) {
            ResultHandle typeHandle;
            try {
                typeHandle = Types.getTypeHandle(constructor, decoratedType, tccl);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Unable to construct the type handle for " + decorator + ": " + e.getMessage());
            }
            constructor.invokeInterfaceMethod(MethodDescriptors.SET_ADD, decoratedTypesHandle, typeHandle);
        }
        constructor.writeInstanceField(decoratedTypes.getFieldDescriptor(), constructor.getThis(), decoratedTypesHandle);

        // delegate type
        ResultHandle delegateTypeHandle;
        try {
            delegateTypeHandle = Types.getTypeHandle(constructor, decorator.getDelegateType(), tccl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unable to construct the type handle for " + decorator + ": " + e.getMessage());
        }
        constructor.writeInstanceField(delegateType.getFieldDescriptor(), constructor.getThis(), delegateTypeHandle);

        constructor.returnValue(null);
    }

    /**
     *
     * @see InjectableDecorator#getDecoratedTypes()
     */
    protected void implementGetDecoratedTypes(ClassCreator creator, FieldDescriptor decoratedTypes) {
        MethodCreator getDecoratedTypes = creator.getMethodCreator("getDecoratedTypes", Set.class).setModifiers(ACC_PUBLIC);
        getDecoratedTypes.returnValue(getDecoratedTypes.readInstanceField(decoratedTypes, getDecoratedTypes.getThis()));
    }

    /**
     *
     * @see InjectableDecorator#getDelegateType()
     */
    protected void implementGetDelegateType(ClassCreator creator, FieldDescriptor delegateType) {
        MethodCreator getDelegateType = creator.getMethodCreator("getDelegateType", Type.class).setModifiers(ACC_PUBLIC);
        getDelegateType.returnValue(getDelegateType.readInstanceField(delegateType, getDelegateType.getThis()));
    }

    /**
     * 
     * @param creator
     * @param qualifiersField
     * @see InjectableDecorator#getDelegateQualifiers()
     */
    protected void implementGetDelegateQualifiers(ClassCreator creator, FieldCreator qualifiersField) {
        if (qualifiersField != null) {
            MethodCreator getDelegateQualifiers = creator.getMethodCreator("getDelegateQualifiers", Set.class)
                    .setModifiers(ACC_PUBLIC);
            getDelegateQualifiers
                    .returnValue(getDelegateQualifiers.readInstanceField(qualifiersField.getFieldDescriptor(),
                            getDelegateQualifiers.getThis()));
        }
    }

    /**
     *
     * @see InjectableDecorator#getPriority()
     */
    protected void implementGetPriority(ClassCreator creator, DecoratorInfo decorator) {
        MethodCreator getPriority = creator.getMethodCreator("getPriority", int.class).setModifiers(ACC_PUBLIC);
        getPriority.returnValue(getPriority.load(decorator.getPriority()));
    }

}
