
LOCAL_PATH := $(call my-dir)

ifeq ($(BUILD_WITH_NEON),1)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
LOCAL_ARM_NEON := true

LOCAL_MODULE := arm_neon

LOCAL_SRC_FILES := \
    memcpy.S

include $(BUILD_STATIC_LIBRARY)

endif

