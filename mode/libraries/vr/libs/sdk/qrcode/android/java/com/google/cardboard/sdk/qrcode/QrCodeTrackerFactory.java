/*
 * Copyright 2019 Google LLC
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
package com.google.cardboard.sdk.qrcode;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * Factory for creating a tracker and associated graphic to be associated with a new QR code. The
 * multi-processor uses this factory to create QR code trackers as needed -- one for each QR code.
 */
public class QrCodeTrackerFactory implements MultiProcessor.Factory<Barcode> {
  private final QrCodeTracker.Listener listener;

  public QrCodeTrackerFactory(QrCodeTracker.Listener listener) {
    this.listener = listener;
  }

  @Override
  public Tracker<Barcode> create(Barcode qrCode) {
    return new QrCodeTracker(listener);
  }
}
