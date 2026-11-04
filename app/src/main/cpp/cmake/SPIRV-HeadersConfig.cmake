# ggml-vulkan asks for SPIRV-Headers because its headers include spirv/unified1/spirv.hpp. A full
# Vulkan SDK would answer with a package config; this build fetches the headers itself and
# publishes their location through SADU_SPIRV_INCLUDE_DIR.
set(SPIRV-Headers_FOUND TRUE)
if(SADU_SPIRV_INCLUDE_DIR)
    include_directories("${SADU_SPIRV_INCLUDE_DIR}")
endif()
