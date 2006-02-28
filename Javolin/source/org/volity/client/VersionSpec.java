package org.volity.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a version specification, according to the Volity spec for
 * (matchable) version numbers.
 *
 * This class is hilariously overwritten. I know it. You don't have to tell me.
 */
public class VersionSpec
{
    /**
     * Extract the spec from a URI of the form ruleset#spec. If there is none,
     * return a "match anything" spec.
     */
    public static VersionSpec fromURI(String uri)
        throws VersionNumber.VersionFormatException, URISyntaxException
    {
        return fromURI(new URI(uri));
    }
    
    /**
     * Extract the spec from a URI of the form ruleset#spec. If there is none,
     * return a "match anything" spec.
     */
    public static VersionSpec fromURI(URI uri)
        throws VersionNumber.VersionFormatException
    {
        String fragment = uri.getRawFragment();
        if (fragment == null)
            return new VersionSpec();
        else
            return new VersionSpec(fragment);
    }

    protected Pattern[] mPatterns;
    protected String mStringForm = null;

    /** 
     * A blank spec. This will match any version number.
     */
    public VersionSpec()
    {
        mPatterns = null;
    }

    /**
     * A simple spec; requires a specific major number, matches any minor
     * number.
     */
    public VersionSpec(int major)
        throws VersionNumber.VersionFormatException
    {
        Pattern pattern = new Pattern(major, 0, false);
        mPatterns = new Pattern[] { pattern };
    }

    /**
     * A simple spec; requires a specific major number, and any minor number
     * greater than the given minor value.
     */
    public VersionSpec(int major, int minor)
        throws VersionNumber.VersionFormatException
    {
        Pattern pattern = new Pattern(major, minor, true);
        mPatterns = new Pattern[] { pattern };
    }

    /**
     * Parse a string into a VersionSpec.
     */
    public VersionSpec(String str)
        throws VersionNumber.VersionFormatException
    {
        if (str == null || str.equals("")) {
            mPatterns = null;
            return;
        }

        List ls = new ArrayList();
        int[] triple = new int[3];

        while (str != null) {
            int pos;
            String pattern;
            Pattern val;

            pos = str.indexOf(",");
            if (pos < 0) {
                pattern = str;
                str = null;
            }
            else {
                pattern = str.substring(0, pos);
                str = str.substring(pos+1);
            }

            int dashpos = pattern.indexOf("-");
            if (dashpos < 0) {
                if (pattern.endsWith(".")) {
                    pattern = pattern.substring(0, pattern.length()-1);
                    Pattern.parseNumber(triple, pattern);
                    val = new Pattern(triple[0], triple[1], triple[2]!=0,
                        false, false, false);
                }
                else {
                    Pattern.parseNumber(triple, pattern);
                    val = new Pattern(triple[0], triple[1], triple[2]!=0);
                }
            }
            else if (dashpos == 0) {
                pattern = pattern.substring(1);
                Pattern.parseNumber(triple, pattern);
                val = new Pattern(triple[0], triple[1], triple[2]!=0, 
                    true, false, false);
            }
            else if (dashpos == pattern.length()-1) {
                pattern = pattern.substring(0, dashpos);
                Pattern.parseNumber(triple, pattern);
                val = new Pattern(triple[0], triple[1], triple[2]!=0, 
                    false, true, false);
            }
            else {
                int endmajor;
                int endminor;
                boolean endshowminor;
                Pattern.parseNumber(triple, pattern.substring(dashpos+1));
                endmajor = triple[0];
                endminor = triple[1];
                endshowminor = (triple[2]!=0);
                Pattern.parseNumber(triple, pattern.substring(0, dashpos));
                val = new Pattern(triple[0], triple[1], triple[2]!=0, 
                    endmajor, endminor, endshowminor);
            }

            ls.add(val);
        }

        mPatterns = new Pattern[ls.size()];
        for (int ix=0; ix<mPatterns.length; ix++) {
            mPatterns[ix] = (Pattern)ls.get(ix);
        }
    }

    /**
     * Does this spec match the given version?
     */
    public boolean matches(VersionNumber version) {
        if (mPatterns == null) {
            // matches anything.
            return true;
        }

        // Must match at least one pattern.
        for (int ix=0; ix<mPatterns.length; ix++) {
            if (mPatterns[ix].matches(version))
                return true;
        }

        // Nope.
        return false;
    }

    /**
     * Return the string form of the VersionSpec.
     *
     * For efficiency (though nobody may ever care), this caches the string
     * form. So we won't have to stringify a VersionSpec twice. No, it's not
     * really a big deal.
     */
    public String toString() {
        if (mStringForm == null) {
            mStringForm = "";
            for (int ix=0; ix<mPatterns.length; ix++) {
                if (ix > 0)
                    mStringForm += ",";
                mStringForm += mPatterns[ix].toString();
            }
        }
        return mStringForm;
    }

    /**
     * One element of a (comma-separated) list of patterns, which comprises
     * a VersionSpec.
     */
    static protected class Pattern {
        protected int mMajor = 1;
        protected int mMinor = 0;
        protected boolean isMinorVisible = true;
        protected boolean isEndMinorVisible = true;
        protected boolean mOrEarlier = false;
        protected boolean mOrLater = false;
        protected boolean mOrMinorLater = false;
        protected boolean mHasEnd = false;
        protected int mEndMajor = 0;
        protected int mEndMinor = 0;

        /**
         * A standard "Major match, minor meet or exceed" match pattern.
         */
        public Pattern(int major, int minor, boolean showminor)
            throws VersionNumber.VersionFormatException
        {
            checkMajorNumberValid(major);
            checkMinorNumberValid(minor, showminor);

            mMajor = major;
            mMinor = minor;
            isMinorVisible = showminor;
            mOrMinorLater = true;
        }

        /**
         * An interval pattern.
         */
        public Pattern(int major, int minor, boolean showminor,
            int major2, int minor2, boolean showminor2)
            throws VersionNumber.VersionFormatException
        {
            checkMajorNumberValid(major);
            checkMinorNumberValid(minor, showminor);
            checkMajorNumberValid(major2);
            checkMinorNumberValid(minor2, showminor2);

            if ((major > major2)
                || (major == major2 && minor > minor2)) {
                throw new VersionNumber.VersionFormatException("Interval pattern is reversed.");
            }

            mMajor = major;
            mMinor = minor;
            isMinorVisible = showminor;
            mEndMajor = major2;
            mEndMinor = minor2;
            isEndMinorVisible = showminor2;

            mHasEnd = true;
        }

        /**
         * A pattern with all the flags specified.
         */
        public Pattern(int major, int minor, boolean showminor,
            boolean orearlier, boolean orlater, boolean orminorlater)
            throws VersionNumber.VersionFormatException
        {
            checkMajorNumberValid(major);
            checkMinorNumberValid(minor, showminor);

            mMajor = major;
            mMinor = minor;
            isMinorVisible = showminor;
            mOrEarlier = orearlier;
            mOrLater = orlater;
            mOrMinorLater = orminorlater;
        }

        public String toString() {
            if (mHasEnd) {
                return buildString(mMajor, mMinor, isMinorVisible) + "-" 
                    + buildString(mEndMajor, mEndMinor, isEndMinorVisible);
            }

            if (mOrMinorLater) {
                return buildString(mMajor, mMinor, isMinorVisible);
            }

            if (mOrLater) {
                return buildString(mMajor, mMinor, isMinorVisible) + "-";
            }

            if (mOrEarlier) {
                return "-" + buildString(mMajor, mMinor, isMinorVisible);
            }

            return buildString(mMajor, mMinor, isMinorVisible) + ".";
        }

        /**
         * Create a string of the form "X" or "X.Y". This is used by the
         * toString() method.
         */
        protected static String buildString(int major, int minor,
            boolean showminor) {
            String res = String.valueOf(major);
            if (showminor)
                res += "." + String.valueOf(minor);
            return res;
        }

        /**
         * Parse a string of the form "X" or "X.Y". This is used by the
         * VersionSpec(String) constructor.
         *
         * This does not call checkMajorNumberValid/checkMinorNumberValid. That
         * happens later, in the Pattern constructor.
         */
        static protected void parseNumber(int[] triple, String str) 
            throws VersionNumber.VersionFormatException
        {
            int pos = str.indexOf(".");
            String strMajor, strMinor;

            if (pos < 0) {
                strMajor = str;
                strMinor = null;
                triple[1] = 0;
                triple[2] = 0;
            }
            else {
                strMajor = str.substring(0, pos);
                strMinor = str.substring(pos+1);
                triple[2] = 1;
            }

            if (!strMajor.matches("\\A[1-9][0-9]*\\z"))
                throw new VersionNumber.VersionFormatException("Major version number is invalid: \"" + strMajor + "\".");
            triple[0] = Integer.parseInt(strMajor);

            if (strMinor != null) {
                if (!strMinor.equals("0") && !strMinor.matches("\\A[1-9][0-9]*\\z"))
                    throw new VersionNumber.VersionFormatException("Minor version number is invalid: \"" + strMinor + "\".");
                triple[1] = Integer.parseInt(strMinor);
            }
        }

        protected void checkMajorNumberValid(int major) 
            throws VersionNumber.VersionFormatException
        {
            if (major <= 0)
                throw new VersionNumber.VersionFormatException("Major version number must be positive.");
        }
        
        protected void checkMinorNumberValid(int minor, boolean show) 
            throws VersionNumber.VersionFormatException
        {
            if (minor < 0)
                throw new VersionNumber.VersionFormatException("Minor version number must be non-negative.");
            if (minor > 0 && !show)
                throw new VersionNumber.VersionFormatException("Minor version is nonzero but not shown.");
        }
        
        /**
         * Does this spec pattern match the given version?
         */
        public boolean matches(VersionNumber version) {
            int vmajor = version.mMajor;
            int vminor = version.mMinor;

            if (mHasEnd) {
                boolean flag1 = false;
                boolean flag2 = false;
                if ((vmajor > mMajor)
                    || (vmajor == mMajor && vminor >= mMinor))
                    flag1 = true;
                if ((vmajor < mEndMajor)
                    || (vmajor == mEndMajor && vminor <= mEndMinor))
                    flag2 = true;
                return (flag1 && flag2);
            }
            if (mOrEarlier) {
                if ((vmajor < mMajor)
                    || (vmajor == mMajor && vminor <= mMinor))
                    return true;
                return false;
            }
            if (mOrLater) {
                if ((vmajor > mMajor)
                    || (vmajor == mMajor && vminor >= mMinor))
                    return true;
                return false;
            }
            if (mOrMinorLater) {
                if (vmajor == mMajor && vminor >= mMinor)
                    return true;
                return false;
            }

            if (vmajor == mMajor && vminor == mMinor)
                return true;
            return false; 
        }

    }

    /**
     * This is a unit test. To run, type
     *   java org.volity.client.VersionSpec
     * in your build directory.
     */
    public static void main(String[] args) {
        int errors = 0;

        String[] listValid = {
            "1", "2", "34", "50", "987",
            "1.1", "1.2", "12.34", "5.0", "987.9", "987.99",
            "-1", "-1.2", "-33.6", "-3.46",
            "1-", "1.2-", "33.6-", "3.46-",
            "1.2.", "12.34.",
            "1-2", "1.2-3", "33.6-45.1", "3.46-5", "3-45.6",
            "4-4", "4.1-4.1", "4.2-4.3", "3-3.0", "3.0-3",
            "1,2", "1,2-", "1,-2", "1.1,2.3", "1,2.5,6.1-8",
            "1,12.34.",
        };

        String[] listInvalid = {
            "1 ", " 1", "1\t", "\t1", "1\n", "  5  ",
            "0", "01", "001",
            "0.1", "1.00", "987.099", "05.5", ".5", ".5.6",
            " 1.2", "1 .2", "1. 2", "1.2 ", " 1. 2\n",
            "1.2.3",
            "3-2", "3.1-3", "3.2-3.1",
            "x", "1x", "x1", "1-x", "1.x", "$1",
            "1,x", "x,1", "1,2,3$", "1,3-2,99",
            "1-2.3.",
        };

        String[] listMatches = {
            "",
            "1", "2.3", "34.56",
            "2", 
            "2", "2.0", "2.1", "2.99", "2.0.0", "2.0.q_", null,
            "2.0", 
            "2", "2.0", "2.1", "2.99", null,
            "3.2",
            "3.2", "3.3", "3.99", null,
            "1.2.",
            "1.2", "1.2.x", null,
            "1.2-1.2",
            "1.2", "1.2.x", null,
            "-3.4",
            "1", "1.4", "2.0", "2.99", "3", "3.0", "3.1", "3.4", null,
            "3.4-",
            "3.4", "3.5", "3.10", "4", "4.0", "4.9", "4.11.12", null,
            "1.2-3.4",
            "1.2", "1.4", "1.88", "2", "2.1", "2.9", "3", "3.3.4", "3.4", null,
            "1.4,2.2",
            "1.4", "1.5", "1.99", "2.2", "2.3", "2.99", null,
            "1.4.,2.6",
            "1.4", "2.6", "2.8", null,
            "2,4-5,7",
            "2.2", "4.1", "5.0", "7.1", null,
        };

        String[] listNonmatches = {
            "2", 
            "1", "1.0", "1.99", "3", "3.0", "3.3", null,
            "3.2",
            "3", "3.1", "3.1.99", "4", null,
            "1.2.",
            "1.0", "1.1", "1.3", "2", null,
            "1.2-1.2",
            "1.0", "1.1", "1.3", "2", null,
            "-3.4",
            "3.5", "3.11", "3.99", "4", "5.1", null,
            "3.4-",
            "1", "1.4", "2.0", "2.99", "3", "3.0", "3.1", null,
            "1.2-3.4",
            "1.0", "1.1", "3.5", "3.11", "3.99", "4", "5.1", null,
            "1.4,2.2",
            "1", "1.3", "2", "2.0", "2.1", "3", "3.1", null,
            "1.4.,2.6",
            "1.3", "1.5", "2.5", "3", null,
            "2,4-5,7",
            "1", "3", "5.1", "6", "8", null,
        };

        for (int ix=0; ix<listValid.length; ix++) {
            String str = listValid[ix];
            try {
                VersionSpec num = new VersionSpec(str);
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
                VersionSpec num = new VersionSpec(str);
                errors++;
                System.out.println("Should not have converted " + str + ": " + num.toString());
            }
            catch (VersionNumber.VersionFormatException ex) {
                // this is what should happen.
            }
        }

        try {
            int ix;

            ix=0;
            while (ix<listMatches.length) {
                VersionSpec spec = new VersionSpec(listMatches[ix]);
                for (ix++; listMatches[ix]!=null; ix++) {
                    VersionNumber number = new VersionNumber(listMatches[ix]);
                    if (!spec.matches(number)) {
                        errors++;
                        System.out.println("Spec " + spec + " should have matched " + number);
                    }
                }
                ix++;
            }

            ix=0;
            while (ix<listNonmatches.length) {
                VersionSpec spec = new VersionSpec(listNonmatches[ix]);
                for (ix++; listNonmatches[ix]!=null; ix++) {
                    VersionNumber number = new VersionNumber(listNonmatches[ix]);
                    if (spec.matches(number)) {
                        errors++;
                        System.out.println("Spec " + spec + " should not have matched " + number);
                    }
                }
                ix++;
            }
        }
        catch (VersionNumber.VersionFormatException ex) {
            errors++;
            System.out.println("Format error: " + ex);
        }

        if (errors == 0)
            System.out.println("VersionSpec passed.");
        else
            System.out.println("VersionSpec FAILED with " + String.valueOf(errors) + " errors.");
    }
}
