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

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the information returned in a metadata query for a journal article from Crossref
 * which is usable for our purposes in PASS Current fields are the journal title (single element list)
 * and the list of issns with types.
 *
 * @author jrm
 */
public class CrossrefJournal {

    private String title;
    private List<TypedIssn> typedIssns = new ArrayList<>();

    public List<TypedIssn> getTypedIssns() {
        return typedIssns;
    }

    public void setTypedIssns(List<TypedIssn> typedIssns) {
        this.typedIssns = typedIssns;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
