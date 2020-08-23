/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2017 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.android

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.os.ResultReceiver

/**
 * @author Aditya Rana
 * A simple utility activity to request permissions in a service.
 */
open class PermissionRequestor : Activity() {
    var resultReceiver: ResultReceiver? = null
    lateinit var permissions: Array<String>
    var requestCode = 0

    override fun onStart() {
        super.onStart()
        resultReceiver = this.intent.getParcelableExtra(KEY_RESULT_RECEIVER)
        permissions = this.intent.getStringArrayExtra(KEY_PERMISSIONS)
        requestCode = this.intent.getIntExtra(KEY_REQUEST_CODE, 0)
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val resultData = Bundle()
        resultData.putStringArray(KEY_PERMISSIONS, permissions)
        resultData.putIntArray(KEY_GRANT_RESULTS, grantResults)
        resultReceiver!!.send(requestCode, resultData)
        finish()
    }

    companion object {
        const val KEY_RESULT_RECEIVER = "resultReceiver"
        const val KEY_PERMISSIONS = "permissions"
        const val KEY_GRANT_RESULTS = "grantResults"
        const val KEY_REQUEST_CODE = "requestCode"
    }
}