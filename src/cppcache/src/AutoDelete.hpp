#ifndef _GEMFIRE_AUTODELETE_HPP_
#define _GEMFIRE_AUTODELETE_HPP_

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <gfcpp/gfcpp_globals.hpp>

namespace gemfire {
template <typename T>
class DeleteObject {
 private:
  T*& m_p;
  bool m_cond;

 public:
  DeleteObject(T*& p) : m_p(p), m_cond(true) {}

  inline void noDelete() { m_cond = false; }

  inline T*& ptr() { return m_p; }

  ~DeleteObject() {
    if (m_cond) {
      GF_SAFE_DELETE(m_p);
    }
  }
};

template <typename T>
class DeleteArray {
 private:
  T*& m_p;
  bool m_cond;

 public:
  DeleteArray(T*& p) : m_p(p), m_cond(true) {}

  inline T operator[](int32_t index) { return m_p[index]; }

  inline void noDelete() { m_cond = false; }

  inline T*& ptr() { return m_p; }

  ~DeleteArray() {
    if (m_cond) {
      GF_SAFE_DELETE_ARRAY(m_p);
    }
  }
};
}

#endif  // #ifndef _GEMFIRE_AUTODELETE_HPP_