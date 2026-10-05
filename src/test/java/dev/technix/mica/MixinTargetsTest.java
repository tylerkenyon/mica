package dev.technix.mica;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks every mixin in {@code mica.client.mixins.json} against the Minecraft jar of the version
 * being built (the test runs once per Stonecutter version): each {@code @Mixin} target class,
 * {@code @Inject} method, {@code @Accessor} field and {@code @Invoker} method must exist.
 *
 * <p>A wrong target only fails at game start-up otherwise ({@code defaultRequire = 1}), so this
 * turns Minecraft API drift into a build failure. On failure the message lists the target's
 * actual members, which is usually all that is needed to update the mixin.
 */
class MixinTargetsTest {

    private static final String MIXIN_PACKAGE = "dev/technix/mica/mixin/client/";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    @TestFactory
    List<DynamicTest> everyMixinTargetExists() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String mixin : mixinClasses()) {
            tests.add(DynamicTest.dynamicTest(mixin + " (Minecraft "
                    + System.getProperty("mica.minecraftVersion", "?") + ")", () -> check(mixin)));
        }
        assertTrue(!tests.isEmpty(), "no mixins found in mica.client.mixins.json");
        return tests;
    }

    private static void check(String mixinName) throws IOException {
        ClassNode mixin = read(MIXIN_PACKAGE + mixinName);
        if (mixin == null) {
            fail("compiled mixin " + mixinName + " not on the test classpath");
        }
        List<ClassNode> targets = new ArrayList<>();
        List<Type> types = listValue(annotation(mixin.invisibleAnnotations, mixin.visibleAnnotations, MIXIN), "value");
        for (Type type : types) {
            ClassNode target = read(type.getInternalName());
            if (target == null) {
                fail(mixinName + ": target class " + type.getClassName() + " does not exist");
            }
            targets.add(target);
        }
        assertTrue(!targets.isEmpty(), mixinName + " has no @Mixin targets");

        for (MethodNode method : mixin.methods) {
            AnnotationNode inject = annotation(method.invisibleAnnotations, method.visibleAnnotations, INJECT);
            AnnotationNode accessor = annotation(method.invisibleAnnotations, method.visibleAnnotations, ACCESSOR);
            AnnotationNode invoker = annotation(method.invisibleAnnotations, method.visibleAnnotations, INVOKER);
            for (ClassNode target : targets) {
                if (inject != null) {
                    for (Object selector : listValue(inject, "method")) {
                        String text = (String) selector;
                        int paren = text.indexOf('(');
                        String name = paren < 0 ? text : text.substring(0, paren);
                        String desc = paren < 0 ? null : text.substring(paren);
                        boolean found = target.methods.stream().anyMatch(m -> m.name.equals(name)
                                && (desc == null || m.desc.equals(desc)));
                        if (!found) {
                            fail(mixinName + "." + method.name + ": @Inject target " + text
                                    + " not found in " + describe(target));
                        }
                    }
                }
                if (accessor != null) {
                    String field = (String) value(accessor, "value");
                    boolean found = target.fields.stream().anyMatch(f -> f.name.equals(field));
                    if (!found) {
                        fail(mixinName + "." + method.name + ": @Accessor field " + field
                                + " not found in " + describe(target));
                    }
                }
                if (invoker != null) {
                    String name = (String) value(invoker, "value");
                    String args = method.desc.substring(0, method.desc.indexOf(')') + 1);
                    boolean found = target.methods.stream()
                            .anyMatch(m -> m.name.equals(name) && m.desc.startsWith(args));
                    if (!found) {
                        fail(mixinName + "." + method.name + ": @Invoker method " + name + args
                                + " not found in " + describe(target));
                    }
                }
            }
        }
    }

    private static List<String> mixinClasses() throws IOException {
        try (InputStream stream = MixinTargetsTest.class.getClassLoader()
                .getResourceAsStream("mica.client.mixins.json")) {
            if (stream == null) {
                fail("mica.client.mixins.json not on the test classpath");
            }
            String json = new String(stream.readAllBytes());
            Matcher client = Pattern.compile("\"client\"\\s*:\\s*\\[([^]]*)]").matcher(json);
            List<String> names = new ArrayList<>();
            if (client.find()) {
                Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(client.group(1));
                while (name.find()) {
                    names.add(name.group(1));
                }
            }
            return names;
        }
    }

    private static ClassNode read(String internalName) throws IOException {
        try (InputStream stream = MixinTargetsTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }

    private static AnnotationNode annotation(List<AnnotationNode> invisible, List<AnnotationNode> visible,
                                             String desc) {
        for (List<AnnotationNode> list : List.of(
                invisible == null ? List.<AnnotationNode>of() : invisible,
                visible == null ? List.<AnnotationNode>of() : visible)) {
            for (AnnotationNode node : list) {
                if (node.desc.equals(desc)) {
                    return node;
                }
            }
        }
        return null;
    }

    private static Object value(AnnotationNode node, String key) {
        if (node == null || node.values == null) {
            return null;
        }
        for (int index = 0; index + 1 < node.values.size(); index += 2) {
            if (key.equals(node.values.get(index))) {
                return node.values.get(index + 1);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> listValue(AnnotationNode node, String key) {
        Object value = value(node, key);
        return value instanceof List<?> list ? (List<T>) list : List.of();
    }

    private static String describe(ClassNode target) {
        String fields = target.fields.stream().map(f -> f.name + ":" + f.desc)
                .collect(Collectors.joining(", "));
        String methods = target.methods.stream().map(m -> m.name + m.desc)
                .collect(Collectors.joining(", "));
        return target.name.replace('/', '.') + " {fields: " + fields + "; methods: " + methods + "}";
    }
}
