/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
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
#include "JniWebRequest.h"

// precached in JNI_OnLoad and released in JNI_OnUnload
JniGlobalReference<jclass>* httpRequestClass;
jmethodID httpRequestClassCtor;

JniGlobalReference<jclass>* headerEntryClass;
JniGlobalReference<jclass>* serverResponseClass;
jfieldID responseField;

void JniWebRequest_OnLoad(JavaVM* vm, JNIEnv* env, void* reserved)
{
  httpRequestClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("HttpRequest")));
  httpRequestClassCtor = env->GetMethodID(httpRequestClass->Get(), "<init>",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Z)V");

  headerEntryClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("HeaderEntry")));
  serverResponseClass = new JniGlobalReference<jclass>(env, env->FindClass(PKG("ServerResponse")));

  responseField = env->GetFieldID(serverResponseClass->Get(), "response", "Ljava/nio/ByteBuffer;");
}

void JniWebRequest_OnUnload(JavaVM* vm, JNIEnv* env, void* reserved)
{
  if (httpRequestClass)
  {
    delete httpRequestClass;
    httpRequestClass = NULL;
  }

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
}

static jlong JNICALL JniCtor(JNIEnv* env, jclass clazz, jobject callbackObject)
{
  try
  {
    return JniPtrToLong(new AdblockPlus::WebRequestSharedPtr(std::make_shared<JniWebRequest>(env, callbackObject)));
  }
  CATCH_THROW_AND_RETURN(env, 0)
}

static void JNICALL JniDtor(JNIEnv* env, jclass clazz, jlong ptr)
{
  delete JniLongToTypePtr<AdblockPlus::WebRequestSharedPtr>(ptr);
}

JniWebRequest::JniWebRequest(JNIEnv* env, jobject callbackObject)
  : JniCallbackBase(env, callbackObject), AdblockPlus::WebRequest()
{
}

AdblockPlus::ServerResponse JniWebRequest::GET(const std::string& url,
    const AdblockPlus::HeaderList& requestHeaders) const
{
  JNIEnvAcquire env(GetJavaVM());

  jmethodID method = env->GetMethodID(
      *JniLocalReference<jclass>(*env,
          env->GetObjectClass(GetCallbackObject())),
      "request",
      "(" TYP("HttpRequest") ")" TYP("ServerResponse"));

  AdblockPlus::ServerResponse sResponse;
  sResponse.status = AdblockPlus::IWebRequest::NS_ERROR_FAILURE;

  if (method)
  {
    jstring jUrl = JniStdStringToJava(*env, url);

    std::string stdRequestMethod = "GET";
    jstring jRequestMethod = JniStdStringToJava(*env, stdRequestMethod);

    JniLocalReference<jobject> jHeaders(*env, NewJniArrayList(*env));
    jmethodID addMethod = JniGetAddToListMethod(*env, *jHeaders);

    for (AdblockPlus::HeaderList::const_iterator it = requestHeaders.begin(),
        end = requestHeaders.end(); it != end; it++)
    {
      JniLocalReference<jobject> headerEntry(*env, NewTuple(*env, it->first, it->second));
      JniAddObjectToList(*env, *jHeaders, addMethod, *headerEntry);
    }

    jobject jHttpRequest = env->NewObject(
      httpRequestClass->Get(),
      httpRequestClassCtor,
      jUrl, jRequestMethod, *jHeaders, true);

    JniLocalReference<jobject> response(*env,
        env->CallObjectMethod(GetCallbackObject(), method, jHttpRequest));

    if (!env->ExceptionCheck())
    {
      sResponse.status = JniGetLongField(*env, serverResponseClass->Get(),
          *response, "status");
      sResponse.responseStatus = JniGetIntField(*env,
                                                serverResponseClass->Get(),
                                                *response,
                                                "responseStatus");
      jobject jByteBuffer = env->GetObjectField(*response, responseField);

      if (jByteBuffer)
      {
        jlong responseSize = env->GetDirectBufferCapacity(jByteBuffer);
        const char* responseBuffer = reinterpret_cast<const char*>(env->GetDirectBufferAddress(jByteBuffer));
        std::string responseText(responseBuffer, responseSize);
        sResponse.responseText = responseText;
      }

      // map headers
      jobjectArray responseHeadersArray = JniGetStringArrayField(*env,
                                                                 serverResponseClass->Get(),
                                                                 *response,
                                                                 "headers");

      if (responseHeadersArray)
      {
        int itemsCount = env->GetArrayLength(responseHeadersArray) / 2;
        for (int i = 0; i < itemsCount; i++)
        {
          jstring jKey = (jstring)env->GetObjectArrayElement(responseHeadersArray, i * 2);
          std::string stdKey = JniJavaToStdString(*env, jKey);
          
          jstring jValue = (jstring)env->GetObjectArrayElement(responseHeadersArray, i * 2 + 1);
          std::string stdValue = JniJavaToStdString(*env, jValue);
          
          std::pair<std::string,std::string>  keyValue(stdKey, stdValue);
          sResponse.responseHeaders.push_back(keyValue);
        }
      }
    }
  }

  CheckAndLogJavaException(*env);

  return sResponse;
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
  { (char*)"ctor", (char*)"(Ljava/lang/Object;)J", (void*)JniCtor },
  { (char*)"dtor", (char*)"(J)V", (void*)JniDtor }
};

extern "C" JNIEXPORT void JNICALL Java_org_adblockplus_libadblockplus_HttpClient_registerNatives(JNIEnv *env, jclass clazz)
{
  env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
