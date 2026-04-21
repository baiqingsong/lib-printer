package com.dawn.printers.hiti;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.hiti.usb.printer.PrintPara;
import com.hiti.usb.printer.PrintPara.PaperSize;
import com.hiti.usb.printer.PrinterJob;
import com.hiti.usb.service.Action;
import com.hiti.usb.service.ErrorCode;
import com.hiti.usb.service.ServiceConnector;

import java.io.InputStream;

public class PrinterOperation {

	private ServiceConnector serviceConnector;
	private Context context;

	// printer job id
	private int mJobId;
	public short		MATTE, PRINTCOUNT, PRINTMODE;
	public int PaperType;
	public String m_strTablesRoot;

	public PrinterOperation(Context context, ServiceConnector serviceConnector) {
		mJobId = 101;
		MATTE = 0;
		PRINTCOUNT = 1;
		PRINTMODE = 1;
		PaperType = 2;
		m_strTablesRoot= "";
		this.context = context;
		this.serviceConnector = serviceConnector;
	}

	public ErrorCode updateFirmware(String version, String path) {
		return serviceConnector.updateFirmware(version, path);
	}

    public ErrorCode updateFirmwareP525N(String version, String path, String bootpath, String kernelpath) {
        return serviceConnector.updateFirmwareP525N(version, path, bootpath, kernelpath);
    }
	// ----------------------------------------------------------------------------
	// Printer operation call, it is a block call and should not be execute by main thread.
	//---------------------------------------------------------------------------------

	public PrinterJob getPrinterStatus() {

		PrinterJob job = printerService(Action.USB_CHECK_PRINTER_STATUS);
		return job;
	}

	public PrinterJob getPrintCount() {

		PrinterJob job = printerService(Action.USB_DEVICE_PRINT_COUNT);
		return job;
	}

	public PrinterJob getRibbonInfo() {

		PrinterJob job = printerService(Action.USB_DEVICE_RIBBON_INFO);
		return job;
	}

	public PrinterJob getFirmwareVersion() {

		PrinterJob job = printerService(Action.USB_DEVICE_FW_VERSION);
		return job;
	}

	public PrinterJob getModelName() {

		PrinterJob job = printerService(Action.USB_DEVICE_MODEL_NAME);
		return job;
	}

	public PrinterJob getSerialNumber() {

		PrinterJob job = printerService(Action.USB_DEVICE_SERIAL_NUM);
		return job;
	}

	public PrinterJob resetPrinter() {

		PrinterJob job = printerService(Action.USB_COMMAND_RESET_PRINTER);
		return job;
	}

	public PrinterJob calibrateRibbonLED() {

		PrinterJob job = printerService(Action.USB_COMMAND_CALIBRATE_RIBBON_LED);
		return job;
	}

	public PrinterJob resumeJob() {

		PrinterJob job = printerService(Action.USB_COMMAND_CLEAN_PAPER_PATH);//USB_COMMAND_RESUME_JOB);
		return job;
	}

	public PrinterJob ejectPaperJam() {

		PrinterJob job = printerService(Action.USB_EJECT_PAPER_JAM);
		return job;
	}

	public PrinterJob cleanPaperPath() {

		PrinterJob job = printerService(Action.USB_COMMAND_CLEAN_PAPER_PATH);//USB_CLEAN_PAPER_PATH);
		return job;
	}
	public PrinterJob print(Bitmap bitmapPhoto) {
		PrinterJob job = printerSetService(Action.USB_PRINT_PHOTOS, bitmapPhoto);
		return job;
	}

	public PrinterJob print(String photoPath) {

		PrinterJob job = printerSetService(Action.USB_PRINT_PHOTOS, photoPath);
		return job;
	}

	public PrinterJob setAutoPowerOff(Short seconds) {

		PrinterJob job = printerSetService(Action.USB_SET_AUTO_POWER_OFF, seconds);
		return job;
	}

	public PrinterJob getStorageID() {

		PrinterJob job = printerService(Action.USB_GET_STORAGE_ID);
		return job;
	}

	public PrinterJob getObjectNumber(long storageId, byte format, long objectId) {

		PrinterJob job = printerObjectService(Action.USB_GET_OBJECT_NUMBER, storageId, format, objectId);
		return job;
	}

	/**
	 * @param storageId
	 * @param format		2: jpeg, 3: album,
	 * @param objectId
	 * @return
	 */
	public PrinterJob getObjectHandleId(long storageId, byte format, long objectId) {

		PrinterJob job = printerObjectService(Action.USB_GET_OBJECT_HANDLE_ID, storageId, format, objectId);
		return job;
	}

	public PrinterJob getObjectInfo(long storageId, long objectId) {

		PrinterJob job = printerObjectService(Action.USB_GET_OBJECT_INFO, storageId, (byte)0x00, objectId);
		return job;
	}

	/**
	 *
	 * @param storageId
	 * @param objectId
	 * @param type  0x01: original photo, 0x02: thumbnail
	 * @return
	 */
	public PrinterJob getObjectData(long storageId, long objectId, byte type) {

		PrinterJob job = printerObjectService(Action.USB_GET_OBJECT_DATA, storageId, type, objectId);
		return job;
	}

	public PrinterJob getJobInQueueNumber() {

		PrinterJob job = printerService(Action.USB_DEVICE_JOB_IN_QUEUE);
		return job;
	}

	//==========================================================================================

	/**
	 * Get bitmap from res\drawable
	 */
	private Bitmap getBitmap(String name) {

		int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());

		InputStream is = context.getResources().openRawResource(id);
		return BitmapFactory.decodeStream(is, null , null);
	}

	/**
	 * Set Printer operation parameter.
	 *
	 * Please be noticed that below parameter can be customize but can't not be null for specific printer operation.
	 */
	private Object getPrinterPara(Action action, Object data) {

		Object attr =  null;
		switch(action) {

			case USB_PRINT_PHOTOS:

				/**
				 * Paper size / photo pixels match table
				 *
				 * +-----------------------------+------------+
				 * |  Paper size                 |  pixels    |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X4_PHOTO        | 1844x1240  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X8_PHOTO        | 1844x2434  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X9_PHOTO        | 1844x2740  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X9_SPLIT_2UP    | 1844x2492  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_5X7_PHOTO        | 1548x2140  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X4_SPLIT_2UP    | 1240x1844  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_5X7_SPLIT_2UP    | 1548x2152  |
				 * +-----------------------------+------------+
				 * | PAPER_SIZE_6X6_PHOTO        | 1844x1844  |
				 * +-----------------------------+------------+
				 */

				//-------------------------------------------------------
				// photo printer test
				//-------------------------------------------------------
				Bitmap bitmap; //getBitmap("pic1844x1240");
				if (data instanceof Bitmap) {
					bitmap = (Bitmap) data;
				} else {
					bitmap = getBitmap(data != null ? data.toString() : "pic1844x1240");
				}

				if (bitmap == null) {

				}else {
					switch(PaperType) {
						case 2:
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_6X4_PHOTO, m_strTablesRoot);
							break;

						case 3:
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_5X7_PHOTO, m_strTablesRoot);
							break;

						case 4:
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_6X8_PHOTO, m_strTablesRoot);
							break;

						case 5:
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_6X4_SPLIT_2UP, m_strTablesRoot);
							break;

						case 6:
							//bitmap = getBitmap(data != null? data.toString(): "pic1844x1844");
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_6X6_PHOTO, m_strTablesRoot);
							break;

						default:
							attr = PrintPara.getPrintPhotoPara(bitmap, PRINTCOUNT, MATTE, PRINTMODE, PaperSize.PAPER_SIZE_6X4_PHOTO, m_strTablesRoot);
							break;
					}
				}

				break;

			case USB_SET_AUTO_POWER_OFF:

				if(data != null && data instanceof Short) attr = PrintPara.getSetCommandPara((short)data);
				break;

			default:
		}

		return attr;
	}


	/**
	 * call printer service and print service error code if operation not success to execute
	 */
	private PrinterJob printerService(Action action) {

		PrinterJob job = null;

		if(action != null) {
			job = new PrinterJob(mJobId++, action);
			serviceConnector.m_strTablesRoot = m_strTablesRoot;
			serviceConnector.doService(job);
		}

		return  job;
	}

	/**
	 * call printer service and print service error code if operation not success to execute
	 */
	private PrinterJob printerSetService(Action action, Object data) {

		PrinterJob job = null;
		if(action != null) {
			job = new PrinterJob(mJobId++, action).setJobPara(getPrinterPara(action, data));
			serviceConnector.doService(job);
		}
		return  job;
	}


	/**
	 * call printer service and print service error code if operation not success to execute
	 */
	private PrinterJob printerObjectService(Action action, long storageId, byte format, long handleId) {

		PrinterJob job = null;

		if(action != null) {
			job = new PrinterJob(mJobId++, action).setJobPara(getObjectPara(action, storageId, format, handleId));
			serviceConnector.doService(job);
		}

		return  job;
	}

	private Object getObjectPara(Action action, long storageId, byte format, long handleId) {

		Object attr = null;

		switch (action) {

			case USB_GET_OBJECT_NUMBER:
			case USB_GET_OBJECT_HANDLE_ID:
			case USB_GET_OBJECT_INFO:
			case USB_GET_OBJECT_DATA:
				attr = PrintPara.getGetObjectValue(storageId, format, handleId);
				break;
			default:
		}

		return attr;
	}
}
