package com.fsck.k9.view;

import java.io.ByteArrayOutputStream;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.crypto.Apg;
import com.fsck.k9.crypto.CryptoProvider;
import com.fsck.k9.crypto.PgpData;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.mail.store.LocalStore.LocalAttachmentBody;




public class MessageCryptoView extends LinearLayout {

    private Context mContext;
    private Activity mActivity;
    private Button mDecryptButton;
    private LinearLayout mCryptoSignatureLayout = null;
    private ImageView mCryptoSignatureStatusImage = null;
    private TextView mCryptoSignatureUserId = null;
    private TextView mCryptoSignatureUserIdRest = null;


    public MessageCryptoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public void setupChildViews() {
        mCryptoSignatureLayout = (LinearLayout) findViewById(R.id.crypto_signature);
        mCryptoSignatureStatusImage = (ImageView) findViewById(R.id.ic_crypto_signature_status);
        mCryptoSignatureUserId = (TextView) findViewById(R.id.userId);
        mCryptoSignatureUserIdRest = (TextView) findViewById(R.id.userIdRest);
        mCryptoSignatureLayout.setVisibility(View.INVISIBLE);
        mDecryptButton = (Button) findViewById(R.id.btn_decrypt);
    }

    public void setActivity(Activity activity) {
        mActivity = activity;

    }


    public void hide() {
        this.setVisibility(View.GONE);
    }

    /**
     * Fill the decrypt layout with signature data, if known, make controls visible, if
     * they should be visible.
     */
    public void updateLayout(final CryptoProvider cryptoProvider, final PgpData pgpData, final Message message) {
        if (pgpData.getSignatureKeyId() != 0) {
            mCryptoSignatureUserIdRest.setText(
                mContext.getString(R.string.key_id, Long.toHexString(pgpData.getSignatureKeyId() & 0xffffffffL)));
            String userId = pgpData.getSignatureUserId();
            if (userId == null) {
                userId = mContext.getString(R.string.unknown_crypto_signature_user_id);
            }
            String chunks[] = userId.split(" <", 2);
            String name = chunks[0];
            if (chunks.length > 1) {
                mCryptoSignatureUserIdRest.setText("<" + chunks[1]);
            }
            mCryptoSignatureUserId.setText(name);
            if (pgpData.getSignatureSuccess()) {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_ok);
            } else if (pgpData.getSignatureUnknown()) {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_error);
            } else {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_error);
            }
            mCryptoSignatureLayout.setVisibility(View.VISIBLE);
            this.setVisibility(View.VISIBLE);
        } else {
            mCryptoSignatureLayout.setVisibility(View.INVISIBLE);
        }
        if ((message == null) && (pgpData.getDecryptedData() == null)) {
            this.setVisibility(View.GONE);
            return;
        }
        if (pgpData.getDecryptedData() != null || pgpData.getSignatureSuccess()) {
            if (pgpData.getSignatureKeyId() == 0) {
                this.setVisibility(View.GONE);
            } else {
                // no need to show this after decryption/verification
                mDecryptButton.setVisibility(View.GONE);
            }
            return;
        }


        mDecryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String data = null;
                    Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
                    if (part == null) {
                        part = MimeUtility.findFirstPartByMimeType(message, "text/html");
                    }
                    if (part != null) {
                        data = MimeUtility.getTextFromPart(part);
                    }
                    // if PGP/Mime get signature
                    Part signaturePart = MimeUtility.findFirstPartByMimeType(
                                             message, "application/pgp-signature");
                    if (signaturePart != null) {

                        try {
                            // TODO: this DOES NOT work! APG can't handle whole
                            // PGP/Mime signed messages!
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            out.write("-----BEGIN PGP SIGNED MESSAGE-----\r\n".getBytes());
                            out.write("Hash: SHA256\r\n\r\n".getBytes());
                            MimeUtility.findFirstPartByMimeType(message, "multipart/alternative").getBody().writeTo(out);
//                            MimeUtility.findFirstPartByMimeType(message, "text/plain").getBody().writeTo(out);
                            signaturePart.getBody().writeTo(out);

                            // byte[] signature = getFileFromUri(sigUri);
                            //TODO also enable for other CryptoProviders
                            ((Apg) cryptoProvider).checkSignedPgpMime(
                                mActivity, out.toString(), pgpData);
                            return;
                        } catch (Exception e) {
                            // Wasn't able to check Signature, make Toast?
                            e.printStackTrace();
                            return;
                        }
                    }
                    // check if there is a PGP/Mime encrypted part
                    Part encryptedPart = MimeUtility.findFirstPartByMimeType(
                                             message, "application/octet-stream");
                    if (message.getContentType().contains("encrypted")  && encryptedPart != null) {
                        if (message.isSet(Flag.X_DOWNLOADED_FULL)) {
                            Uri uri = ((LocalAttachmentBody) encryptedPart.getBody()).getContentUri();
                            cryptoProvider.decryptPgpMime(mActivity, uri, pgpData);
                        } else {
                            Toast.makeText(mContext, R.string.download_message_before_decryption, Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    // else
                    cryptoProvider.decrypt(mActivity, data, pgpData);
                } catch (MessagingException me) {
                    Log.e(K9.LOG_TAG, "Unable to decrypt email.", me);
                }
            }
        });


        mDecryptButton.setVisibility(View.VISIBLE);
        if (cryptoProvider.isEncrypted(message)) {
            mDecryptButton.setText(R.string.btn_decrypt);
            this.setVisibility(View.VISIBLE);
        } else if (cryptoProvider.isSigned(message)) {
            mDecryptButton.setText(R.string.btn_verify);
            this.setVisibility(View.VISIBLE);
        } else {
            this.setVisibility(View.GONE);
            try {
                // check for PGP/MIME signature
                Part pgp = MimeUtility.findFirstPartByMimeType(message, "application/pgp-signature");
                if (pgp != null) {
                    Toast.makeText(mContext, R.string.pgp_mime_verification_unsupported, Toast.LENGTH_LONG).show();
                }
            } catch (MessagingException e) {
            }
        }
    }

}
