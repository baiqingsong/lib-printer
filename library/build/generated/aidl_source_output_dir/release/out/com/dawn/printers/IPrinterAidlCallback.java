/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.dawn.printers;
// Declare any non-default types here with import statements

public interface IPrinterAidlCallback extends android.os.IInterface
{
  /** Default implementation for IPrinterAidlCallback. */
  public static class Default implements com.dawn.printers.IPrinterAidlCallback
  {
    @Override public void onDataReceived(android.os.Bundle data) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.dawn.printers.IPrinterAidlCallback
  {
    private static final java.lang.String DESCRIPTOR = "com.dawn.printers.IPrinterAidlCallback";
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.dawn.printers.IPrinterAidlCallback interface,
     * generating a proxy if needed.
     */
    public static com.dawn.printers.IPrinterAidlCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.dawn.printers.IPrinterAidlCallback))) {
        return ((com.dawn.printers.IPrinterAidlCallback)iin);
      }
      return new com.dawn.printers.IPrinterAidlCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
        case TRANSACTION_onDataReceived:
        {
          data.enforceInterface(descriptor);
          android.os.Bundle _arg0;
          if ((0!=data.readInt())) {
            _arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
          }
          else {
            _arg0 = null;
          }
          this.onDataReceived(_arg0);
          reply.writeNoException();
          return true;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements com.dawn.printers.IPrinterAidlCallback
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void onDataReceived(android.os.Bundle data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((data!=null)) {
            _data.writeInt(1);
            data.writeToParcel(_data, 0);
          }
          else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDataReceived, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            getDefaultImpl().onDataReceived(data);
            return;
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      public static com.dawn.printers.IPrinterAidlCallback sDefaultImpl;
    }
    static final int TRANSACTION_onDataReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    public static boolean setDefaultImpl(com.dawn.printers.IPrinterAidlCallback impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Stub.Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Stub.Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }
    public static com.dawn.printers.IPrinterAidlCallback getDefaultImpl() {
      return Stub.Proxy.sDefaultImpl;
    }
  }
  public void onDataReceived(android.os.Bundle data) throws android.os.RemoteException;
}
