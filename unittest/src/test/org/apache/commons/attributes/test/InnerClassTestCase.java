/*
 * Copyright 2003-2004 The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.attributes.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import junit.framework.TestCase;

import org.apache.commons.attributes.Attributes;
import org.apache.commons.attributes.InvalidAttributeTargetError;

public class InnerClassTestCase extends TestCase {
    
    public void testInnerClassAttributesOnMehtod () throws Exception {
        Class sample = InnerClassSample.class;
        Method method = sample.getMethod ("method", new Class[]{ InnerClassSample.Internal.class });
        assertTrue (Attributes.getAttributes (method, ThreadSafe.class).size () > 0);
    }
    
    public void testInnerClassAttributesOnField () throws Exception {
        Class sample = InnerClassSample.class;
        Field field = sample.getField ("field");
        assertTrue (Attributes.getAttributes (field, ThreadSafe.class).size () > 0);
    }
}