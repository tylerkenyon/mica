package dev.technix.mica.internal.backend.vulkan;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;




final class VulkanShaderCompiler {

    private VulkanShaderCompiler() {
    }


    static final String VERTEX_GLSL = """
            #version 450
            layout(location = 0) in vec2 Position;
            layout(location = 1) in vec2 UV;
            layout(location = 2) in vec4 Color;

            layout(push_constant) uniform PushConstants {
                vec2 Scale;
                vec2 Translate;
            } pc;

            layout(location = 0) out vec2 Frag_UV;
            layout(location = 1) out vec4 Frag_Color;

            void main() {
                Frag_UV = UV;
                Frag_Color = Color;
                gl_Position = vec4(Position * pc.Scale + pc.Translate, 0.0, 1.0);
            }
            """;


    static final String FRAGMENT_GLSL = """
            #version 450
            layout(location = 0) in vec2 Frag_UV;
            layout(location = 1) in vec4 Frag_Color;

            layout(set = 0, binding = 0) uniform sampler2D Texture;

            layout(location = 0) out vec4 Out_Color;

            void main() {
                Out_Color = Frag_Color * texture(Texture, Frag_UV);
            }
            """;

    static ByteBuffer compileVertexShader() {
        return compile(VERTEX_GLSL, "imgui.vert", Shaderc.shaderc_vertex_shader);
    }

    static ByteBuffer compileFragmentShader() {
        return compile(FRAGMENT_GLSL, "imgui.frag", Shaderc.shaderc_fragment_shader);
    }


    static ByteBuffer compileComputeShader(String source, String sourceName) {
        return compile(source, sourceName, Shaderc.shaderc_compute_shader);
    }

    private static ByteBuffer compile(String source, String sourceName, int kind) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == MemoryUtil.NULL) {
            throw new IllegalStateException("shaderc_compiler_initialize returned NULL");
        }
        long options = Shaderc.shaderc_compile_options_initialize();
        try {

            Shaderc.shaderc_compile_options_set_target_env(options,
                    Shaderc.shaderc_target_env_vulkan,
                    Shaderc.shaderc_env_version_vulkan_1_0);
            long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind,
                    sourceName, "main", options);
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                String message = Shaderc.shaderc_result_get_error_message(result);
                Shaderc.shaderc_result_release(result);
                throw new IllegalStateException("Failed to compile " + sourceName + " to SPIR-V: "
                        + message);
            }
            long length = Shaderc.shaderc_result_get_length(result);
            ByteBuffer spirv = MemoryUtil.memAlloc((int) length);


            ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
            bytes.limit((int) length);
            spirv.put(bytes).flip();
            Shaderc.shaderc_result_release(result);
            return spirv;
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
