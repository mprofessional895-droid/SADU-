# ggml-vulkan asks for SPIRV-Headers but nothing in this build links against it: the shaders are
# compiled to SPIR-V by glslc and embedded by vulkan-shaders-gen, which does not use the headers.
# Standalone glslc packages therefore do not need the whole Vulkan SDK, and this file stands in
# for the package's CMake config.
set(SPIRV-Headers_FOUND TRUE)
