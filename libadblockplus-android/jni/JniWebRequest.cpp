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
#include "JniWebRequest.h"

namespace
{
  // precached in JNI_OnLoad and released in JNI_OnUnload
  JniGlobalReference<jclass>* headerEntryClass;
  JniGlobalReference<jclass>* serverResponseClass;
  JniGlobalReference<jclass>* getCallbackClass;
  jmethodID getCallbackClassCtor;
}

void JniWebRequest_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  headerEntryClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("HeaderEntry")));
  serverResponseClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("ServerResponse")));
  getCallbackClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("WebRequest$GetCallback")));
  getCallbackClassCtor = env->GetMethodID(getCallbackClass->Get(), "<init>", "(J)V");
}

void JniWebRequest_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (headerEntryClass)
  {
    delete headerEntryClass;
    headerEntryClass = NULL;
  }

  if (serverResponseClass)
  {
    delete serverResponseClass;
    serverResponseClass = NULL;
  }

  if (getCallbackClass)
  {
    delete getCallbackClass;
    getCallbackClass = nullptr;
  }
}

static void JniGetCallbackDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
  delete JniLongToTypePtr<AdblockPlus::IWebRequest::GetCallback>(ptr);
}

static void JniGetCallbackCall(JNIEnv* env, jclass clazz, jlong ptr, jobject jServerResponse)
{
  AdblockPlus::ServerResponse serverResponse;
  serverResponse.status = AdblockPlus::IWebRequest::NS_ERROR_FAILURE;

  if (!env->ExceptionCheck())
  {
    serverResponse.status = JniGetLongField(env, serverResponseClass->Get(),
                                            jServerResponse, "status");
    serverResponse.responseStatus = JniGetIntField(env,
                                                   serverResponseClass->Get(),
                                                   jServerResponse,
                                                   "responseStatus");
    serverResponse.responseText = JniGetStringField(env,
                                                    serverResponseClass->Get(),
                                                    jServerResponse,
                                                    "response");

    // map headers
    jobjectArray responseHeadersArray = JniGetStringArrayField(env,
                                                               serverResponseClass->Get(),
                                                               jServerResponse,
                                                               "headers");

    if (responseHeadersArray)
    {
      int itemsCount = env->GetArrayLength(responseHeadersArray) / 2;
      for (int i = 0; i < itemsCount; i++)
      {
        jstring jKey = (jstring)env->GetObjectArrayElement(responseHeadersArray, i * 2);
        std::string stdKey = JniJavaToStdString(env, jKey);

        jstring jValue = (jstring)env->GetObjectArrayElement(responseHeadersArray, i * 2 + 1);
        std::string stdValue = JniJavaToStdString(env, jValue);

        std::pair<std::string,std::string>  keyValue(stdKey, stdValue);
        serverResponse.responseHeaders.push_back(keyValue);
      }
    }
  }

  (*JniLongToTypePtr<AdblockPlus::IWebRequest::GetCallback>(ptr))(serverResponse);
}

JniWebRequest::JniWebRequest(JNIEnv* env, jobject callbackObject)
  : JniCallbackBase(env, callbackObject)
{
}

void JniWebRequest::GET(const std::string& url,
    const AdblockPlus::HeaderList& requestHeaders, const GetCallback& getCallback)
{
  JNIEnvAcquire env(GetJavaVM());

  auto jGetCallback = env->NewObject(getCallbackClass->Get(),
                                     getCallbackClassCtor,
                                     JniPtrToLong(new GetCallback(getCallback)));

  jmethodID method = env->GetMethodID(
      *JniLocalReference<jclass>(*env, env->GetObjectClass(GetCallbackObject())),
      "httpGET",
      "(Ljava/lang/String;Ljava/util/List;" TYP("GetCallback") ")V");

  if (jGetCallback && method)
  {
    JniLocalReference<jobject> arrayList(*env, NewJniArrayList(*env));
    jmethodID addMethod = JniGetAddToListMethod(*env, *arrayList);

    for (AdblockPlus::HeaderList::const_iterator it = requestHeaders.begin(),
             end = requestHeaders.end(); it != end; it++)
    {
      JniLocalReference<jobject> headerEntry(*env, NewTuple(*env, it->first, it->second));
      JniAddObjectToList(*env, *arrayList, addMethod, *headerEntry);
    }
    jvalue args[3];
    args[0].l = env->NewStringUTF(url.c_str());
    args[1].l = *arrayList;
    args[2].l = jGetCallback;
    env->CallVoidMethodA(GetCallbackObject(), method, args);
  }
  CheckAndLogJavaException(*env);
}

jobject JniWebRequest::NewTuple(JNIEnv* env, const std::string& a,
    const std::string& b) const
{
  jmethodID factory = env->GetMethodID(headerEntryClass->Get(), "<init>",
      "(Ljava/lang/String;Ljava/lang/String;)V");

  JniLocalReference<jstring> strA(env, env->NewStringUTF(a.c_str()));
  JniLocalReference<jstring> strB(env, env->NewStringUTF(b.c_str()));

  return env->NewObject(headerEntryClass->Get(), factory, *strA, *strB);
}

static JNINativeMethod methods[] =
{
  { (char*)"GetCallbackDtor", (char*)"(J)V", (void*)JniGetCallbackDtor },
  { (char*)"GetCallbackCall", (char*)"(JLjava/lang/Object;)V", (void*)JniGetCallbackCall }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_WebRequest_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}