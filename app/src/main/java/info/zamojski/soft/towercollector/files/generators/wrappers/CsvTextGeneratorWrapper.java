/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package info.zamojski.soft.towercollector.files.generators.wrappers;

import android.content.Context;

import org.acra.ACRA;

import java.io.IOException;
import java.util.List;

import info.zamojski.soft.towercollector.dao.MeasurementsDatabase;
import info.zamojski.soft.towercollector.enums.GeneratorResult;
import info.zamojski.soft.towercollector.files.DeviceOperationException;
import info.zamojski.soft.towercollector.files.DeviceOperationException.Reason;
import info.zamojski.soft.towercollector.files.FileGeneratorResult;
import info.zamojski.soft.towercollector.files.devices.IWritableTextDevice;
import info.zamojski.soft.towercollector.files.formatters.csv.ICsvFormatter;
import info.zamojski.soft.towercollector.files.generators.CsvTextGenerator;
import info.zamojski.soft.towercollector.model.Measurement;
import trikita.log.Log;

public class CsvTextGeneratorWrapper extends TextGeneratorWrapperBase {

    private final String TAG = CsvTextGeneratorWrapper.class.getSimpleName();

    private CsvTextGenerator<ICsvFormatter, IWritableTextDevice> generator;

    public CsvTextGeneratorWrapper(Context context, IWritableTextDevice device, ICsvFormatter formatter) {
        this.context = context;
        this.device = device;
        this.generator = new CsvTextGenerator(formatter, device);
    }

    public FileGeneratorResult generate() {
        try {
            // get number of measurements to upload
            int measurementsCount = MeasurementsDatabase.getInstance(context).getAllMeasurementsCount();
            // get last measurement row id
            Measurement lastMeasurement = MeasurementsDatabase.getInstance(context).getLastMeasurement();
            // check if there is anything to upload
            if (measurementsCount == 0 || lastMeasurement == null) {
                Log.d("generate(): Cancelling save due to no data");
                return new FileGeneratorResult(GeneratorResult.NoData, Reason.Unknown);
            }
            // calculate number of upload parts
            final int MEASUREMENTS_PER_PART = 400;
            int partsCount = 1;
            if (measurementsCount > MEASUREMENTS_PER_PART) {
                partsCount = (int) Math.ceil(1.0 * measurementsCount / MEASUREMENTS_PER_PART);
            }
            device.open();
            notifyProgressListeners(0, measurementsCount);
            // write header
            generator.writeHeader();
            // get measurements in loop
            for (int i = 0; i < partsCount; i++) {
                // get from database
                List<Measurement> measurements = MeasurementsDatabase.getInstance(context).getOlderMeasurements(lastMeasurement.getTimestamp(), i * MEASUREMENTS_PER_PART, MEASUREMENTS_PER_PART);
                // write to file
                generator.writeEntryChunk(measurements);
                notifyProgressListeners(i * MEASUREMENTS_PER_PART + measurements.size(), measurementsCount);
                if (cancel) {
                    break;
                }
            }
            device.close();
            // fix for dialog not closed when upload running in background and data deleted
            notifyProgressListeners(measurementsCount, measurementsCount);
            if (cancel) {
                Log.d("generate(): Export cancelled");
                return new FileGeneratorResult(GeneratorResult.Cancelled, Reason.Unknown);
            } else {
                Log.d("generate(): All %s measurements exported", measurementsCount);
                return new FileGeneratorResult(GeneratorResult.Succeeded, Reason.Unknown);
            }
        } catch (DeviceOperationException ex) {
            Log.e("generate(): Failed to check external memory compatibility", ex);
            ACRA.getErrorReporter().handleSilentException(ex);
            return new FileGeneratorResult(GeneratorResult.Failed, ex.getReason());
        } catch (IOException ex) {
            Log.e("generate(): Failed to save data on external memory", ex);
            ACRA.getErrorReporter().handleSilentException(ex);
            return new FileGeneratorResult(GeneratorResult.Failed, Reason.Unknown, ex.getMessage());
        } finally {
            // just for sure
            device.close();
        }
    }

}
