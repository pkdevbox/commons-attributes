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
package org.apache.commons.attributes.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;

import xjavadoc.SourceClass;
import xjavadoc.XClass;
import xjavadoc.XConstructor;
import xjavadoc.XJavaDoc;
import xjavadoc.XField;
import xjavadoc.XMethod;
import xjavadoc.XParameter;
import xjavadoc.XProgramElement;
import xjavadoc.XTag;
import xjavadoc.ant.XJavadocTask;
import xjavadoc.filesystem.AbstractFile;

/**
 * Ant task to compile attributes. Usage:
 *
 * <pre><code>
 *     &lt;taskdef resource="org/apache/commons/attributes/anttasks.properties"/&gt;
 *     
 *     &lt;attribute-compiler destDir="temp/"&gt; attributepackages="my.attributes;my.otherattributes"
 *         &lt;fileset dir="src/" includes="*.java"/&gt;
 *     &lt;/attribute-compiler&gt;
 * </code></pre>
 *
 * <ul>
 * <li>destDir<dd>Destination directory for generated source files
 * <li>attributepackages<dd>A set of package names that will be automatically searched for attributes.
 * </ul>
 *
 * The task should be run before compiling the Java sources, and will produce some
 * additional Java source files in the destination directory that should be compiled
 * along with the input source files. (See the overview for a diagram.)
 */
public class AttributeCompiler extends XJavadocTask {
    
    private final ArrayList fileSets = new ArrayList ();
    private Path src;
    private File destDir;
    private int numGenerated;
    private int numIgnored;
    private String attributePackages = "";
    
    public AttributeCompiler () {
    }
    
    public void setAttributePackages (String attributePackages) {
        this.attributePackages = attributePackages;
    }
    
    public void addFileset (FileSet set) {
        super.addFileset (set);
        fileSets.add (set);
    }
    
    public void setDestdir (File destDir) {
        this.destDir = destDir;
    }
    
    public void setSourcepathref (String pathref) {
        String sourcePaths = project.getReference (pathref).toString ();
        StringTokenizer tok = new StringTokenizer (sourcePaths, File.pathSeparator);
        while (tok.hasMoreTokens ()) {
            FileSet fs = new FileSet ();
            fs.setDir (new File (tok.nextToken ()));
            fs.setIncludes ("**/*.java");
            fs.setProject (project);
            addFileset (fs);
        }
    }
    
    protected void copyImports (File source, PrintWriter dest) throws Exception {
        BufferedReader br = new BufferedReader (new FileReader (source));
        try {
            String line = null;
            while ((line = br.readLine ()) != null) {
                if (line.startsWith ("import ")) {
                    dest.println (line);
                }
            }
        } finally {
            br.close ();
        }
    }
    
    protected void addExpressions (Collection tags, PrintWriter pw, String collectionName, File sourceFile) {
        addExpressions (tags, null, pw, collectionName, sourceFile);
    }
    
    protected void addExpressions (Collection tags, String selector, PrintWriter pw, String collectionName, File sourceFile) {
        String fileName = sourceFile != null ? sourceFile.getPath ().replace ('\\', '/') : "<unknown>";
        Iterator iter = tags.iterator ();
        while (iter.hasNext ()) {
            XTag tag = (XTag) iter.next ();
            
            if (isAttribute (tag)) {
                String expression = tag.getName () + " " + tag.getValue ();
                expression = expression.trim ();
                
                // Remove the second @-sign.
                expression = expression.substring (1);
                
                if (selector != null) {
                    if (expression.startsWith (".")) {
                        // We have selector, tag does...
                        String tagSelector = expression.substring (1, expression.indexOf (" "));
                        expression = expression.substring (expression.indexOf (" ")).trim ();
                        if (!selector.equals (tagSelector)) {
                            // ...but they didn't match.
                            continue;
                        }
                    } else {
                        // We have selector, but tag doesn't
                        continue;
                    }
                } else {
                    // No selector, but tag has selector.
                    if (expression.startsWith (".")) {
                        continue;
                    }
                }
                
                pw.println ("        {");
                outputAttributeExpression (pw, expression, fileName, tag.getLineNumber (), "_attr");
                pw.println ("        Object _oattr = _attr; // Need to erase type information");
                pw.println ("        if (_oattr instanceof org.apache.commons.attributes.Sealable) {");
                pw.println ("            ((org.apache.commons.attributes.Sealable) _oattr).seal ();");
                pw.println ("        }");
                pw.println ("        " + collectionName + ".add ( _attr );");
                pw.println ("        }");
            }
        }
    }
    
    protected void outputAttributeExpression (PrintWriter pw, String expression, String filename, int line, String tempVariableName) {
        AttributeExpressionParser.ParseResult result = AttributeExpressionParser.parse (expression, filename, line);
        pw.print ("            " + result.className + " " + tempVariableName + " = new " + result.className + "(");
        
        boolean first = true;
        Iterator iter = result.arguments.iterator ();
        while (iter.hasNext ()) {
            AttributeExpressionParser.Argument arg = (AttributeExpressionParser.Argument) iter.next ();
            if (arg.field == null) {
                if (!first) {
                    pw.print (", ");
                }
                first = false;
                pw.print (arg.text);
            }
        }
        pw.println ("  // " + filename + ":" + line);
        pw.println (");");
        
        iter = result.arguments.iterator ();
        while (iter.hasNext ()) {
            AttributeExpressionParser.Argument arg = (AttributeExpressionParser.Argument) iter.next ();
            if (arg.field != null) {
                String methodName = "set" + arg.field.substring (0, 1).toUpperCase () + arg.field.substring (1);
                pw.println ("            " + tempVariableName + "." + methodName + "(\n" + 
                    arg.text + "  // " + filename + ":" + line + "\n" +
                    ");");
            }
        }
    }
    
    protected boolean elementHasAttributes (Collection xElements) {
        Iterator iter = xElements.iterator ();
        while (iter.hasNext ()) {
            XProgramElement element = (XProgramElement) iter.next ();
            if (tagHasAttributes (element.getDoc ().getTags ())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Encodes a class name to the internal Java name.
     * For example, an inner class Outer.Inner will be
     * encoed as Outer$Inner.
     */
    private void getTransformedQualifiedName (XClass type, StringBuffer sb) {
        
        if (type.isInner ()) {
            String packageName = type.getContainingPackage ().getName ();
            sb.append (packageName);
            if (packageName.length () > 0) {
                sb.append (".");
            }
            sb.append (type.getName ().replace ('.','$'));
        } else {
            sb.append (type.getQualifiedName ());
        }
    }
    
    protected String getParameterTypes (Collection parameters) {
        StringBuffer sb = new StringBuffer ();
        for (Iterator params = parameters.iterator (); params.hasNext ();) {
            XParameter parameter = (XParameter) params.next ();
            getTransformedQualifiedName (parameter.getType (), sb);
            sb.append (parameter.getDimensionAsString ());
            
            if (params.hasNext ()) {
                sb.append (",");
            }
        }
        return sb.toString ();
    }
    
    protected void generateClass (XClass xClass) throws Exception {
        String name = null;
        File sourceFile = null;
        File destFile = null;
        String packageName = null;
        String className = null;
        
        packageName = xClass.getContainingPackage().getName ();
        
        if (xClass.isInner ()) {
            name = xClass.getQualifiedName ().substring (packageName.length ());
            
            sourceFile = getSourceFile (xClass);
            
            className = xClass.getName ().replace ('.', '$');
            name = packageName + (packageName.length () > 0 ? "." : "") + className;            
        } else {
            name = xClass.getQualifiedName ();
            sourceFile = getSourceFile (xClass);
            className = xClass.getName ();
        }
        
        if (sourceFile == null) {
            log ("Unable to find source file for: " + name);
        }
        
        destFile = new File (destDir, name.replace ('.', '/') + "$__attributeRepository.java");
        
        if (xClass.isAnonymous ()) {
            log (xClass.getName () + " is anonymous - ignoring.", Project.MSG_VERBOSE);
            numIgnored++;
            return;
        }
        
        if (!hasAttributes (xClass)) {
            if (destFile.exists ()) {
                destFile.delete ();
            }
            return;
        }
        
        if (destFile.exists () && sourceFile != null && destFile.lastModified () >= sourceFile.lastModified ()) {
            return;
        }
        
        numGenerated++;
        
        
        destFile.getParentFile ().mkdirs ();
        PrintWriter pw = new PrintWriter (new FileWriter (destFile));
        try {
            if (packageName != null && !packageName.equals ("")) {
                pw.println ("package " + packageName + ";");
            }
            
            if (sourceFile != null) {
                copyImports (sourceFile, pw);
            }
            
            StringTokenizer tok = new StringTokenizer (attributePackages, ";");
            while (tok.hasMoreTokens ()) {
                pw.println ("import " + tok.nextToken () + ".*;");
            }
            
            pw.println ("public class " + className + "$__attributeRepository implements org.apache.commons.attributes.AttributeRepositoryClass {");
            {
                pw.println ("    private final java.util.Set classAttributes = new java.util.HashSet ();");
                pw.println ("    private final java.util.Map fieldAttributes = new java.util.HashMap ();");
                pw.println ("    private final java.util.Map methodAttributes = new java.util.HashMap ();");
                pw.println ("    private final java.util.Map constructorAttributes = new java.util.HashMap ();");
                pw.println ();
                
                pw.println ("    public " + className + "$__attributeRepository " + "() {");
                pw.println ("        initClassAttributes ();");
                pw.println ("        initMethodAttributes ();");
                pw.println ("        initFieldAttributes ();");
                pw.println ("        initConstructorAttributes ();");
                pw.println ("    }");
                pw.println ();
                
                pw.println ("    public java.util.Set getClassAttributes () { return classAttributes; }");
                pw.println ("    public java.util.Map getFieldAttributes () { return fieldAttributes; }");
                pw.println ("    public java.util.Map getConstructorAttributes () { return constructorAttributes; }");
                pw.println ("    public java.util.Map getMethodAttributes () { return methodAttributes; }");
                pw.println ();
                
                pw.println ("    private void initClassAttributes () {");
                addExpressions (xClass.getDoc ().getTags (), pw, "classAttributes", sourceFile);
                pw.println ("    }");
                pw.println ();
                
                // ---- Field Attributes
                
                pw.println ("    private void initFieldAttributes () {");
                pw.println ("        java.util.Set attrs = null;");
                for (Iterator iter = xClass.getFields ().iterator (); iter.hasNext ();) {
                    XField member = (XField) iter.next ();
                    if (member.getDoc ().getTags ().size () > 0) {
                        String key = member.getName ();
                        
                        pw.println ("        attrs = new java.util.HashSet ();");
                        addExpressions (member.getDoc ().getTags (), pw, "attrs", sourceFile);
                        pw.println ("        fieldAttributes.put (\"" + key + "\", attrs);");
                        pw.println ("        attrs = null;");
                        pw.println ();
                    }
                }
                pw.println ("    }");
                
                // ---- Method Attributes
                
                pw.println ("    private void initMethodAttributes () {");
                pw.println ("        java.util.Set attrs = null;");
                pw.println ("        java.util.List bundle = null;");
                for (Iterator iter = xClass.getMethods ().iterator (); iter.hasNext ();) {
                    XMethod member = (XMethod) iter.next ();
                    if (member.getDoc ().getTags ().size () > 0) {
                        StringBuffer sb = new StringBuffer ();
                        sb.append (member.getName ()).append ("(");
                        sb.append (getParameterTypes (member.getParameters ()));
                        sb.append (")");
                        String key = sb.toString ();
                        
                        pw.println ("        bundle = new java.util.ArrayList ();");
                        pw.println ("        attrs = new java.util.HashSet ();");
                        addExpressions (member.getDoc ().getTags (), null, pw, "attrs", sourceFile);
                        pw.println ("        bundle.add (attrs);");
                        pw.println ("        attrs = null;");
                        
                        pw.println ("        attrs = new java.util.HashSet ();");
                        addExpressions (member.getDoc ().getTags (), "return", pw, "attrs", sourceFile);
                        pw.println ("        bundle.add (attrs);");
                        pw.println ("        attrs = null;");
                        
                        for (Iterator parameters = member.getParameters ().iterator (); parameters.hasNext ();) {
                            XParameter parameter = (XParameter) parameters.next ();
                            pw.println ("        attrs = new java.util.HashSet ();");
                            addExpressions (member.getDoc ().getTags (), parameter.getName (), pw, "attrs", sourceFile);
                            pw.println ("        bundle.add (attrs);");
                            pw.println ("        attrs = null;");
                        }
                        
                        pw.println ("        methodAttributes.put (\"" + key + "\", bundle);");
                        pw.println ("        bundle = null;");
                        pw.println ();
                    }                
                }
                pw.println ("    }");
                
                
                // ---- Constructor Attributes
                
                pw.println ("    private void initConstructorAttributes () {");
                pw.println ("        java.util.Set attrs = null;");
                pw.println ("        java.util.List bundle = null;");
                for (Iterator iter = xClass.getConstructors ().iterator (); iter.hasNext ();) {
                    XConstructor member = (XConstructor) iter.next ();
                    if (member.getDoc ().getTags ().size () > 0) {
                        StringBuffer sb = new StringBuffer ();
                        sb.append ("(");
                        sb.append (getParameterTypes (member.getParameters ()));
                        sb.append (")");
                        String key = sb.toString ();
                        
                        pw.println ("        bundle = new java.util.ArrayList ();");
                        pw.println ("        attrs = new java.util.HashSet ();");
                        addExpressions (member.getDoc ().getTags (), null, pw, "attrs", sourceFile);
                        pw.println ("        bundle.add (attrs);");
                        pw.println ("        attrs = null;");
                        
                        for (Iterator parameters = member.getParameters ().iterator (); parameters.hasNext ();) {
                            XParameter parameter = (XParameter) parameters.next ();
                            pw.println ("        attrs = new java.util.HashSet ();");
                            addExpressions (member.getDoc ().getTags (), parameter.getName (), pw, "attrs", sourceFile);
                            pw.println ("        bundle.add (attrs);");
                            pw.println ("        attrs = null;");
                        }
                        
                        pw.println ("        constructorAttributes.put (\"" + key + "\", bundle);");
                        pw.println ("        bundle = null;");
                        pw.println ();
                    }
                }
                pw.println ("    }");            
            }
            pw.println ("}");
            
            pw.close ();
        } catch (Exception e) {
            pw.close ();
            destFile.delete ();
            throw e;
        }
    }
    
    /**
     * Finds the source file of a class.
     *
     * @param qualifiedName the fully qualified class name
     * @return the file the class is defined in.
     * @throws BuildException if the file could not be found.
     */
    protected File getSourceFile (XClass xClass) throws BuildException {
        while (xClass != null && xClass.isInner ()) {
            xClass = xClass.getContainingClass ();
        }
        
        if (xClass != null && xClass instanceof SourceClass) {
            AbstractFile af = ((SourceClass) xClass).getFile ();
            return new File (af.getPath ());
        }
        return null;
    }
    
    protected boolean hasAttributes (XClass xClass) {
        if (tagHasAttributes (xClass.getDoc ().getTags ()) ||
            elementHasAttributes (xClass.getFields ()) ||
            elementHasAttributes (xClass.getMethods ()) ||
            elementHasAttributes (xClass.getConstructors ()) ) {
            return true;
        }
        return false;
    }
    
    /**
     * Tests if a tag is an attribute. Currently the test is
     * only "check if it is defined with two @-signs".
     */
    protected boolean isAttribute (XTag tag) {
        return tag.getName ().length () > 0 && tag.getName ().charAt (0) == '@';
    }
    
    protected void start() throws BuildException {
        destDir.mkdirs ();
        numGenerated = 0;
        
        XJavaDoc doc = getXJavaDoc ();
        Iterator iter = doc.getSourceClasses ().iterator ();
        try {
            while (iter.hasNext ()) {
                XClass xClass = (XClass) iter.next ();
                generateClass (xClass);
            }
        } catch (Exception e) {
            throw new BuildException (e.toString (), e);
        }
        log ("Generated attribute information for " + numGenerated + " classes. Ignored " + numIgnored + " classes.");
    }
    
    /**
     * Checks if a collection of XTags contain any tags specifying attributes.
     */
    protected boolean tagHasAttributes (Collection tags) {
        Iterator iter = tags.iterator ();
        while (iter.hasNext ()) {
            XTag tag = (XTag) iter.next ();
            if (isAttribute (tag)) {
                return true;
            }
        }
        return false;
    }
}