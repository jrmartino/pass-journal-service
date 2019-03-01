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
 * This class is a convenience class for negotiating our use of the type:value format for issns.
 *
 * @author jrm
 */
public class TypedIssn {

    private String value;
    private IssnType type;


    public TypedIssn(String value, IssnType type) {
        this.value = value;
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public IssnType getType() {
        return type;
    }

    public void setType(IssnType type) {
        this.type = type;
    }

    public String serializeForPass() {//generate a serialized version for use on the issn field for Journal
        return String.join(":", type.getPassTypeString(), value);
    }
}
