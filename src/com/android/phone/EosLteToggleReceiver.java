/*
 * Written by bigrushdog for Team Eos 2012
 */

package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.android.internal.telephony.Phone;

import org.teameos.jellybean.settings.EOSConstants;

/*
 * Receiver to process LTE toggle requests from Eos quick settings
 * toggle. Since LTE functions require access to the Phone process
 * this is probably the best place to put it. At least for now I 
 * think it beats a separate apk accessing the Phone looper.
 */
public class EosLteToggleReceiver extends BroadcastReceiver {
    static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;
    private static final int CDMA_ONLY = Phone.NT_MODE_CDMA;
    private static final int LTE_CDMA = Phone.NT_MODE_GLOBAL;
    private static final int LTE_ONLY = Phone.NT_MODE_LTE_ONLY;
    private static final String EOS_TELEPHONY_INTENT = EOSConstants.INTENT_TELEPHONY_LTE_TOGGLE;
    private static final String EOS_TELEPHONY_MODE_KEY = EOSConstants.INTENT_TELEPHONY_LTE_TOGGLE_KEY;
    private Phone mPhone;
    private MyHandler mHandler;
    private Context mContext;
    private int mRequestedNetworkMode; // value requested from intent

    @Override
    public void onReceive(Context context, Intent intent) {
        mPhone = PhoneApp.getPhone();
        mHandler = new MyHandler();
        mContext = mPhone.getContext();
        if (intent.getAction().equals(EOS_TELEPHONY_INTENT)) {
            int requestedNetworkMode = intent.getIntExtra(EOS_TELEPHONY_MODE_KEY,
                    preferredNetworkMode);
            mRequestedNetworkMode = requestedNetworkMode;
            // make sure we're dealing with an actual cdma lte phone
            boolean isLteOnCdma = mPhone.getLteOnCdmaMode() == Phone.LTE_ON_CDMA_TRUE;
            int phoneType = mPhone.getPhoneType();
            if (phoneType == Phone.PHONE_TYPE_CDMA && isLteOnCdma) {
                // make sure only valid values come from receiver
                if (requestedNetworkMode == CDMA_ONLY
                        || requestedNetworkMode == LTE_CDMA
                        || requestedNetworkMode == LTE_ONLY) {
                    // make sure we don't write the same value to the modem
                    // a lot of this is redundant as the toggle checks too
                    int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                            mContext.getContentResolver(),
                            android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                            preferredNetworkMode);
                    if (requestedNetworkMode != settingsNetworkMode) {
                        // Set the modem network mode
                        mPhone.setPreferredNetworkType(
                                requestedNetworkMode,
                                mHandler.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                    }
                }
            }
        }
    }

    private class MyHandler extends Handler {

        static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
        static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;

                case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            // if no exception thrown, the requested network mode was good
            // so we write the new value back to Settings.Secure
            if (ar.exception == null) {
                android.provider.Settings.Secure.putInt(mContext.getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        mRequestedNetworkMode);
            } else {
                // if for some reason we fail, restore to previous value
                mPhone.getPreferredNetworkType(obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE));
            }
        }

        // for now we won't use this

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int modemNetworkMode = ((int[]) ar.result)[0];

                int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                        mContext.getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode);

                // check that modemNetworkMode is from an accepted value
                if (modemNetworkMode == CDMA_ONLY
                        || modemNetworkMode == LTE_CDMA
                        || modemNetworkMode == LTE_ONLY) {

                    // check changes in modemNetworkMode and updates
                    // settingsNetworkMode
                    if (modemNetworkMode != settingsNetworkMode) {
                        settingsNetworkMode = modemNetworkMode;

                        // changes the Settings.System accordingly to
                        // modemNetworkMode
                        android.provider.Settings.Secure.putInt(
                                mContext.getContentResolver(),
                                android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                                settingsNetworkMode);
                    }
                }
            }
        }
    }
}
