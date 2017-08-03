/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2017 eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "JniCallbacks.h"
#include "Utils.h"

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* timerCallbackClass;
jmethodID timerCallbackClassCtor;

void JniTimer_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  timerCallbackClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("TimerCallbackImpl")));
  timerCallbackClassCtor = env->GetMethodID(timerCallbackClass->Get(), "<init>", "(J)V");
}

void JniTimer_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (timerCallbackClass)
  {
    delete timerCallbackClass;
    timerCallbackClass = nullptr;
  }
}

static void JNICALL JniTimerCallbackDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
  delete JniLongToTypePtr<AdblockPlus::ITimer::TimerCallback>(ptr);
}

static void JNICALL JniTimerCallbackCall(JNIEnv* env, jclass clazz, jlong ptr)
{
  (*JniLongToTypePtr<AdblockPlus::ITimer::TimerCallback>(ptr))();
}

JniTimer::JniTimer(JNIEnv* env, jobject callbackObject)
  : JniCallbackBase(env, callbackObject)
{
}

void JniTimer::SetTimer(const std::chrono::milliseconds &timeout,
                        const TimerCallback &timerCallback)
{
  JNIEnvAcquire env(GetJavaVM());

  jobject jTimerCallback = env->NewObject(timerCallbackClass->Get(),
                                       timerCallbackClassCtor,
                                       JniPtrToLong(new TimerCallback(timerCallback)));

  jmethodID method = env->GetMethodID(
      *JniLocalReference<jclass>(*env, env->GetObjectClass(GetCallbackObject())),
      "setTimer",
      "(J" TYP("Timer$Callback") ")V");

  if (jTimerCallback && method)
  {
    jvalue args[2];
    args[0].j = static_cast<long>(timeout.count());
    args[1].l = jTimerCallback;
    env->CallVoidMethodA(GetCallbackObject(), method, args);
  }

  CheckAndLogJavaException(*env);
}

static JNINativeMethod methods[] =
{
  { (char*)"TimerCallbackDtor", (char*)"(J)V", (void*)JniTimerCallbackDtor },
  { (char*)"TimerCallbackCall", (char*)"(J)V", (void*)JniTimerCallbackCall }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_TimerCallbackImpl_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
