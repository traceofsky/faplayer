
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
ifeq ($(BUILD_WITH_NEON),1)
LOCAL_ARM_NEON := true
endif

LOCAL_MODULE := freetype_plugin

LOCAL_CFLAGS += \
    -std=c99 \
    -DHAVE_CONFIG_H \
    -DMODULE_STRING=\"freetype\" \
    -DMODULE_NAME=freetype

LOCAL_C_INCLUDES += \
    $(VLCROOT)/compat \
    $(VLCROOT) \
    $(VLCROOT)/include \
    $(VLCROOT)/src \
    $(EXTROOT)/iconv/include \
    $(EXTROOT)/freetype/include

LOCAL_SRC_FILES := \
    freetype.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

