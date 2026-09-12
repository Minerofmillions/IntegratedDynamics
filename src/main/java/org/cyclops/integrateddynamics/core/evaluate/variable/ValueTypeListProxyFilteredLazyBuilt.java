package org.cyclops.integrateddynamics.core.evaluate.variable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.cyclops.integrateddynamics.Reference;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.*;
import org.cyclops.integrateddynamics.core.evaluate.operator.CombinedOperator;
import org.cyclops.integrateddynamics.core.evaluate.operator.Operators;

import java.util.concurrent.TimeUnit;

/**
 * A list that is built lazily from a start value and an operator.
 * @param <T> The value type type.
 * @param <V> The value type.
 */
public class ValueTypeListProxyFilteredLazyBuilt<T extends IValueType<V>, V extends IValue> extends ValueTypeListProxyBase<T, V> {

    private Cache<Integer, V> cache_values = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).maximumSize(100).build();

    private final ValueTypeListProxyLazyBuilt<T, V> inner;
    private final IOperator filter;

    public ValueTypeListProxyFilteredLazyBuilt(ValueTypeListProxyLazyBuilt<T, V> inner, IOperator filter) {
        super(ValueTypeListProxyFactories.FILTERED_LAZY_BUILT.getName(), inner.getValueType());
        this.inner = inner;
        this.filter = filter;
    }

    public ValueTypeListProxyFilteredLazyBuilt(ValueTypeListProxyFilteredLazyBuilt<T, V> inner, IOperator filter) {
        this(inner.inner, CombinedOperator.Conjunction.asOperator(inner.filter, filter));
    }

    public static <T extends IValueType<V>, V extends IValue> ValueTypeListProxyFilteredLazyBuilt<T, V> ofProxy(IValueTypeListProxy<T, V> proxy, IOperator filter) throws EvaluationException {
        switch (proxy) {
            case ValueTypeListProxyLazyBuilt<T, V> lazyBuilt -> {
                return new ValueTypeListProxyFilteredLazyBuilt<>(lazyBuilt, filter);
            }
            case ValueTypeListProxyFilteredLazyBuilt<T, V> filteredLazyBuilt -> {
                return new ValueTypeListProxyFilteredLazyBuilt<>(filteredLazyBuilt, filter);
            }
            default -> throw new EvaluationException(Component.empty());
        }
    }

    @Override
    public int getLength() throws EvaluationException {
        return Integer.MAX_VALUE;
    }

    @Override
    public V get(int index) throws EvaluationException {
        V current = cache_values.getIfPresent(index);
        if (current != null) {
            return current;
        }
        int matchedValues = 0;
        int currentIndex = 0;
        while (matchedValues <= index) {
            V value = inner.get(currentIndex);
            IValue result = ValueHelpers.evaluateOperator(filter, value);
            ValueHelpers.validatePredicateOutput(filter, result);
            if (((ValueTypeBoolean.ValueBoolean) result).getRawValue()) {
                current = value;
                matchedValues++;
            }
            currentIndex++;
        }
        cache_values.put(index, current);
        return current;
    }

    @Override
    public boolean isInfinite() {
        return true;
    }

    public static class Factory extends ValueTypeListProxyNBTFactorySimple<IValueType<IValue>, IValue, ValueTypeListProxyFilteredLazyBuilt<IValueType<IValue>, IValue>> {

        @Override
        public ResourceLocation getName() {
            return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "filtered_lazybuilt");
        }

        @Override
        protected void serializeNbt(ValueDeseralizationContext valueDeseralizationContext, ValueTypeListProxyFilteredLazyBuilt<IValueType<IValue>, IValue> value, CompoundTag tag) throws IValueTypeListProxyFactoryTypeRegistry.SerializationException {
            tag.put("inner", ValueTypeListProxyFactories.LAZY_BUILT.serialize(valueDeseralizationContext, value.inner));
            tag.put("filter", Operators.REGISTRY.serialize(valueDeseralizationContext, value.filter));
        }

        @Override
        protected ValueTypeListProxyFilteredLazyBuilt<IValueType<IValue>, IValue> deserializeNbt(ValueDeseralizationContext valueDeseralizationContext, CompoundTag tag) throws IValueTypeListProxyFactoryTypeRegistry.SerializationException, EvaluationException {
            ValueTypeListProxyLazyBuilt inner = ValueTypeListProxyFactories.LAZY_BUILT.deserialize(valueDeseralizationContext, tag.get("inner"));
            IOperator filter = Operators.REGISTRY.deserialize(valueDeseralizationContext, tag.get("filter"));
            return new ValueTypeListProxyFilteredLazyBuilt<>(inner, filter);
        }
    }
}
