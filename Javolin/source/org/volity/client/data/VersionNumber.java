package org.volity.client.data;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Represents a version number, according to the Volity spec for (matchable)
 * version numbers.
 *
 * This class is hilariously overwritten. I know it. You don't have to tell me.
 */
public class VersionNumber
{
    /**
     * Extract the number from a URI of the form ruleset#num. If there is
     * none, return "1".
     */
    public static VersionNumber fromURI(String uri)
        throws VersionNumber.VersionFormatException, URISyntaxException
    {
        return fromURI(new URI(uri));
    }

    /**
     * Extract the number from a URI of the form ruleset#num. If there is
     * none, return "1".
     */
    public static VersionNumber fromURI(URI uri)
        throws VersionNumber.VersionFormatException
    {
        String fragment = uri.getFragment();
        if (fragment == null)
            return new VersionNumber();
        else
            return new VersionNumber(fragment);
    }

    /**
     * Extract the non-spec part from a URI of the form ruleset#spec. If there
     * is no spec, return the original URI.
     */
    public static URI onlyURI(String uri)
        throws URISyntaxException
    {
        return onlyURI(new URI(uri));
    }

    /**
     * Extract the non-spec part from a URI of the form ruleset#spec. If there
     * is no spec, return the original URI.
     */
    public static URI onlyURI(URI uri)
        throws URISyntaxException
    {
        if (uri.getFragment() == null)
            return uri;
        return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
    }
    

    protected int mMajor = 1;
    protected int mMinor = 0;
    protected boolean isMinorVisible = true;
    protected String mRelease = null;
    protected String mStringForm = null;

    public VersionNumber()
    {
        mMajor = 1;
        isMinorVisible = false;
    }

    public VersionNumber(int major)
        throws VersionFormatException
    {
        checkMajorNumberValid(major);

        mMajor = major;
        isMinorVisible = false;
    }

    public VersionNumber(int major, int minor)
        throws VersionFormatException
    {
        checkMajorNumberValid(major);
        checkMinorNumberValid(minor);

        mMajor = major;
        mMinor = minor;
    }

    public VersionNumber(int major, int minor, String release)
        throws VersionFormatException
    {
        checkMajorNumberValid(major);
        checkMinorNumberValid(minor);
        if (release != null)
            checkReleaseValid(release);

        mMajor = major;
        mMinor = minor;
        mRelease = release;
    }

    public VersionNumber(String str)
        throws VersionFormatException
    {
        String strMajor, strMinor;
        int pos;

        pos = str.indexOf('.');
        if (pos < 0) {
            strMajor = str;
            str = null;
        }
        else {
            strMajor = str.substring(0, pos);
            str = str.substring(pos+1);
        }

        if (!strMajor.matches("\\A[1-9][0-9]*\\z"))
            throw new VersionFormatException("Major version number is invalid: \"" + strMajor + "\".");
        mMajor = Integer.parseInt(strMajor);
        checkMajorNumberValid(mMajor);

        if (str == null) {
            isMinorVisible = false;
            return;
        }

        pos = str.indexOf('.');
        if (pos < 0) {
            strMinor = str;
            str = null;
        }
        else {
            strMinor = str.substring(0, pos);
            str = str.substring(pos+1);
        }

        if (!strMinor.equals("0") && !strMinor.matches("\\A[1-9][0-9]*\\z"))
            throw new VersionFormatException("Minor version number is invalid: \"" + strMinor + "\".");
        mMinor = Integer.parseInt(strMinor);
        checkMinorNumberValid(mMinor);

        if (str == null) {
            return;
        }

        checkReleaseValid(str);
        mRelease = str;
    }


    /**
     * Check that various bits are acceptable as the parts of a version number.
     * Each routine throws VersionFormatException if unacceptable.
     */

    protected void checkMajorNumberValid(int major) 
        throws VersionFormatException
    {
        if (major <= 0)
            throw new VersionFormatException("Major version number must be positive.");
    }

    protected void checkMinorNumberValid(int minor) 
        throws VersionFormatException
    {
        if (minor < 0)
            throw new VersionFormatException("Minor version number must be non-negative.");
    }

    protected void checkReleaseValid(String release) 
        throws VersionFormatException
    {
        if (!release.matches("\\A[\\w[_.+-]]+\\z"))
            throw new VersionFormatException("Release string is invalid: \"" + release + "\".");
    }

    /**
     * Does this version match the given spec? (If the given spec is null, we
     * take that as a "matches anything" spec.)
     */
    public boolean matches(VersionSpec spec) {
        if (spec == null)
            return true;
        // The code that does this check lives in VersionSpec, so we call that.
        return spec.matches(this);
    }

    /**
     * Return the string form of the VersionNumber.
     *
     * For efficiency (though nobody may ever care), this caches the string
     * form. So we won't have to stringify a VersionNumber twice. No, it's not
     * really a big deal.
     */
    public String toString() {
        if (mStringForm == null) {
            mStringForm = String.valueOf(mMajor);
            if (isMinorVisible)
                mStringForm += ("." + String.valueOf(mMinor));
            if (mRelease != null)
                mStringForm += ("." + mRelease);
        }
        return mStringForm;
    }

    /**
     * Exception type for a badly-formed VersionNumber (or VersionSpec)
     */
    static public class VersionFormatException extends Exception {
        VersionFormatException(String st) {
            super(st);
        }
    }

    /**
     * This is a unit test. To run, type
     *   java org.volity.client.data.VersionNumber
     * in your build directory.
     */
    public static void main(String[] args) {
        int errors = 0;

        String[] listValid = {
            "1", "2", "3", "99", "1234",
            "1.0", "2.0", "3.0", "99.0", "1234.0",
            "1.1", "2.3", "3.5", "99.7", "1234.9",
            "1.11", "2.34", "3.562", "99.776", "1234.998",
            "1.0.0", "2.0.1", "3.1.2", "99.87.321", "1234.0.987",
            "1.0.a", "2.1.B", "3.3.a0_+x-", "99.87.3.2.1", "1234.0._.__.+",
        };

        String[] listInvalid = {
            " ", " 1", "1 ", "$", "$1",
            "0", "01", "q", "-1", "1a", "a1", "-0", "-001",
            "1.00", "2.q", "3.012", "4.2q", "5.q2", "6.-3",
            "1.", "1.2.",
            "1. 1", "1.1 ", "1.3\t", "\n4.5",
            "1.2.3q ", "1.2. 3", "1.2.$", "1.33.555665$",
            "1.2.34\t45", "1.2.3,4",
        };

        for (int ix=0; ix<listValid.length; ix++) {
            String str = listValid[ix];
            try {
                VersionNumber num = new VersionNumber(str);
                String newstr = num.toString();
                if (!str.equals(newstr)) {
                    errors++;
                    System.out.println("Bad conversion of " + str + ": " + newstr);
                }
            }
            catch (Exception ex) {
                errors++;
                System.out.println("Cannot create " + str + ": " + ex.toString());
            }
        }

        for (int ix=0; ix<listInvalid.length; ix++) {
            String str = listInvalid[ix];
            try {
                VersionNumber num = new VersionNumber(str);
                errors++;
                System.out.println("Should not have converted " + str + ": " + num.toString());
            }
            catch (VersionFormatException ex) {
                // this is what should happen.
            }
        }

        if (errors == 0)
            System.out.println("VersionNumber passed.");
        else
            System.out.println("VersionNumber FAILED with " + String.valueOf(errors) + " errors.");
    }
}
