/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.util;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.nifi.components.ConfigurableComponent;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ControllerServiceLookup;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.SchedulingContext;
import org.junit.Assert;

public class MockProcessContext extends MockControllerServiceLookup implements SchedulingContext, ControllerServiceLookup {

    private final ConfigurableComponent component;
    private final Map<PropertyDescriptor, String> properties = new HashMap<>();

    private String annotationData = null;
    private boolean yieldCalled = false;
    private boolean enableExpressionValidation = false;
    private boolean allowExpressionValidation = true;

    private volatile Set<Relationship> unavailableRelationships = new HashSet<>();

    /**
     * Creates a new MockProcessContext for the given Processor
     *
     * @param component being mocked
     */
    public MockProcessContext(final ConfigurableComponent component) {
        this.component = Objects.requireNonNull(component);
    }

    public MockProcessContext(final ControllerService component, final MockProcessContext context) {
        this(component);

        try {
            annotationData = context.getControllerServiceAnnotationData(component);
            final Map<PropertyDescriptor, String> props = context.getControllerServiceProperties(component);
            properties.putAll(props);

            super.addControllerServices(context);
        } catch (IllegalArgumentException e) {
            // do nothing...the service is being loaded
        }
    }

    @Override
    public PropertyValue getProperty(final PropertyDescriptor descriptor) {
        return getProperty(descriptor.getName());
    }

    @Override
    public PropertyValue getProperty(final String propertyName) {
        final PropertyDescriptor descriptor = component.getPropertyDescriptor(propertyName);
        if (descriptor == null) {
            return null;
        }

        final String setPropertyValue = properties.get(descriptor);
        final String propValue = (setPropertyValue == null) ? descriptor.getDefaultValue() : setPropertyValue;
        return new MockPropertyValue(propValue, this, (enableExpressionValidation && allowExpressionValidation) ? descriptor : null);
    }

    @Override
    public PropertyValue newPropertyValue(final String rawValue) {
        return new MockPropertyValue(rawValue, this);
    }

    public ValidationResult setProperty(final String propertyName, final String propertyValue) {
        return setProperty(new PropertyDescriptor.Builder().name(propertyName).build(), propertyValue);
    }

    /**
     * Updates the value of the property with the given PropertyDescriptor to
     * the specified value IF and ONLY IF the value is valid according to the
     * descriptor's validator. Otherwise, the property value is not updated. In
     * either case, the ValidationResult is returned, indicating whether or not
     * the property is valid
     *
     * @param descriptor of property to modify
     * @param value new value
     * @return result
     */
    public ValidationResult setProperty(final PropertyDescriptor descriptor, final String value) {
        requireNonNull(descriptor);
        requireNonNull(value, "Cannot set property to null value; if the intent is to remove the property, call removeProperty instead");
        final PropertyDescriptor fullyPopulatedDescriptor = component.getPropertyDescriptor(descriptor.getName());

        final ValidationResult result = fullyPopulatedDescriptor.validate(value, new MockValidationContext(this));
        String oldValue = properties.put(fullyPopulatedDescriptor, value);
        if (oldValue == null) {
            oldValue = fullyPopulatedDescriptor.getDefaultValue();
        }
        if ((value == null && oldValue != null) || (value != null && !value.equals(oldValue))) {
            component.onPropertyModified(fullyPopulatedDescriptor, oldValue, value);
        }

        return result;
    }

    public boolean removeProperty(final PropertyDescriptor descriptor) {
        Objects.requireNonNull(descriptor);
        final PropertyDescriptor fullyPopulatedDescriptor = component.getPropertyDescriptor(descriptor.getName());
        String value = null;

        if ((value = properties.remove(fullyPopulatedDescriptor)) != null) {
            if (!value.equals(fullyPopulatedDescriptor.getDefaultValue())) {
                component.onPropertyModified(fullyPopulatedDescriptor, value, null);
            }

            return true;
        }
        return false;
    }

    @Override
    public void yield() {
        yieldCalled = true;
    }

    public boolean isYieldCalled() {
        return yieldCalled;
    }

    public void addControllerService(final String serviceIdentifier, final ControllerService controllerService, final Map<PropertyDescriptor, String> properties, final String annotationData) {
        requireNonNull(controllerService);
        final ControllerServiceConfiguration config = addControllerService(controllerService);
        config.setProperties(properties);
        config.setAnnotationData(annotationData);
    }

    @Override
    public int getMaxConcurrentTasks() {
        return 1;
    }

    public void setAnnotationData(final String annotationData) {
        this.annotationData = annotationData;
    }

    @Override
    public String getAnnotationData() {
        return annotationData;
    }

    @Override
    public Map<PropertyDescriptor, String> getProperties() {
        final List<PropertyDescriptor> supported = component.getPropertyDescriptors();
        if (supported == null || supported.isEmpty()) {
            return Collections.unmodifiableMap(properties);
        } else {
            final Map<PropertyDescriptor, String> props = new LinkedHashMap<>();
            for (final PropertyDescriptor descriptor : supported) {
                props.put(descriptor, null);
            }
            props.putAll(properties);
            return props;
        }
    }

    /**
     * Validates the current properties, returning ValidationResults for any
     * invalid properties. All processor defined properties will be validated.
     * If they are not included in the in the purposed configuration, the
     * default value will be used.
     *
     * @return Collection of validation result objects for any invalid findings
     * only. If the collection is empty then the processor is valid. Guaranteed
     * non-null
     */
    public Collection<ValidationResult> validate() {
        return component.validate(new MockValidationContext(this));
    }

    public boolean isValid() {
        for (final ValidationResult result : validate()) {
            if (!result.isValid()) {
                return false;
            }
        }

        return true;
    }

    public void assertValid() {
        final StringBuilder sb = new StringBuilder();
        int failureCount = 0;

        for (final ValidationResult result : validate()) {
            if (!result.isValid()) {
                sb.append(result.toString()).append("\n");
                failureCount++;
            }
        }

        if (failureCount > 0) {
            Assert.fail("Processor has " + failureCount + " validation failures:\n" + sb.toString());
        }
    }

    @Override
    public String encrypt(final String unencrypted) {
        return "enc{" + unencrypted + "}";
    }

    @Override
    public String decrypt(final String encrypted) {
        if (encrypted.startsWith("enc{") && encrypted.endsWith("}")) {
            return encrypted.substring(4, encrypted.length() - 2);
        }
        return encrypted;
    }

    public void setValidateExpressionUsage(final boolean validate) {
        allowExpressionValidation = validate;
    }

    public void enableExpressionValidation() {
        enableExpressionValidation = true;
    }

    public void disableExpressionValidation() {
        enableExpressionValidation = false;
    }

    Map<PropertyDescriptor, String> getControllerServiceProperties(final ControllerService controllerService) {
        return super.getConfiguration(controllerService.getIdentifier()).getProperties();
    }

    String getControllerServiceAnnotationData(final ControllerService controllerService) {
        return super.getConfiguration(controllerService.getIdentifier()).getAnnotationData();
    }

    @Override
    public ControllerServiceLookup getControllerServiceLookup() {
        return this;
    }

    @Override
    public void leaseControllerService(final String identifier) {
    }

    @Override
    public Set<Relationship> getAvailableRelationships() {
        if (!(component instanceof Processor)) {
            return Collections.emptySet();
        }

        final Set<Relationship> relationships = new HashSet<>(((Processor) component).getRelationships());
        relationships.removeAll(unavailableRelationships);
        return relationships;
    }

    public void setUnavailableRelationships(final Set<Relationship> relationships) {
        this.unavailableRelationships = Collections.unmodifiableSet(new HashSet<>(relationships));
    }

    public Set<Relationship> getUnavailableRelationships() {
        return unavailableRelationships;
    }
}
