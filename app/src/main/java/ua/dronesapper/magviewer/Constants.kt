/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ua.dronesapper.magviewer

/**
 * Defines several constants used between the service and UI.
 */
interface Constants {
    companion object {
        const val BUFFER_SIZE = 1024
        const val DEQUEUE_SIZE = 100
        const val RECORDS_FOLDER = "SensorRecords"
        const val SHARED_KEY_FILE = "ua.dronesapper.magviewer.SHARED_KEY"

        const val SERVER_IPADDR_SHARED_KEY = "server_ipaddr"
        const val SERVER_PORT_SHARED_KEY = "server_port"

        const val DATA_TYPE_SHARED_KEY = "data_type"
        const val ENDIAN_SHARED_KEY = "data_endian"
    }
}