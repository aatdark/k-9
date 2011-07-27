
package com.fsck.k9.mail.internet;

import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;

import java.io.*;
import java.util.Locale;
import java.util.Random;

public class MimeMultipart extends Multipart {
    protected String mPreamble;

    protected String mContentType;

    protected String mBoundary;

    protected String mSubType;

    public MimeMultipart() throws MessagingException {
        mBoundary = generateBoundary();
        setSubType("mixed");
    }

    public MimeMultipart(String contentType) throws MessagingException {
        this.mContentType = contentType;
        try {
            mSubType = MimeUtility.getHeaderParameter(contentType, null).split("/")[1];
            mBoundary = MimeUtility.getHeaderParameter(contentType, "boundary");
            if (mBoundary == null) {
                throw new MessagingException("MultiPart does not contain boundary: " + contentType);
            }
        } catch (Exception e) {
            throw new MessagingException(
                "Invalid MultiPart Content-Type; must contain subtype and boundary. ("
                + contentType + ")", e);
        }
    }

    public String generateBoundary() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append("----");
        for (int i = 0; i < 30; i++) {
            sb.append(Integer.toString(random.nextInt(36), 36));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    public String getPreamble() {
        return mPreamble;
    }

    public void setPreamble(String preamble) {
        this.mPreamble = preamble;
    }

    @Override
    public String getContentType() {
        return mContentType;
    }

    public void setSubType(String subType) {
        this.mSubType = subType;
        if (subType.equals("encrypted")) {
            mContentType = String.format("multipart/%s; protocol=\"application/pgp-encrypted\"; boundary=\"%s\"" , subType, mBoundary);
        } else {
            if (subType.equals("signed")) {
                mContentType = String.format("multipart/%s; protocol=\"application/pgp-signature\"; micalg=pgp-sha1; boundary=\"%s\"" , subType, mBoundary);
            } else {
                mContentType = String.format("multipart/%s; boundary=\"%s\"", subType, mBoundary);
            }
        }
    }

    public void writeTo(OutputStream out) throws IOException, MessagingException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);

       /*RFC 1521:
          NOTE: The CRLF preceding the encapsulation line is conceptually
          attached to the boundary so that it is possible to have a part
          that does not end with a CRLF (line break)
        */
        String boundaryWithCRLF = "\r\n--" + mBoundary + "\r\n";
        String boundaryWithCRLFEND = "\r\n--" + mBoundary + "--\r\n";
        if (mPreamble != null) {
            writer.write(mPreamble + "\r\n");
        }

        if (mParts.size() == 0) {
            writer.write(boundaryWithCRLF);
        }

        for (int i = 0, count = mParts.size(); i < count; i++) {
            BodyPart bodyPart = mParts.get(i);
            writer.write(boundaryWithCRLF);
            writer.flush();
            bodyPart.writeTo(out);
            writer.write("\r\n");
        }

        writer.write(boundaryWithCRLFEND);
        writer.flush();
    }

    public InputStream getInputStream() throws MessagingException {
        return null;
    }
}
