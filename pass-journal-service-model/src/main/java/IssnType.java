/*
 *
 * Copyright 2019 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

/**
 * This enum manages the various representations for type identifiers for issns
 *
 * @author jrm
 */
public enum IssnType {
    PRINT,
    ELECTRONIC;

    private String passTypeString;

    static {// these values represent how types are stored on the issn field for the PASS Journal object
        PRINT.passTypeString = "Print";
        ELECTRONIC.passTypeString = "Online";
    }

    public String getPassTypeString() {
        return passTypeString;
    }

    private String crossrefTypeString;

    static {// these values represent how issn types are presented in Crossref metadata
        PRINT.crossrefTypeString = "print";
        ELECTRONIC.crossrefTypeString = "electronic";
    }

    public String getCrossrefTypeString() {
        return crossrefTypeString;
    }
}
