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

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.model.Journal;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author jrm
 */
public class PassJournalService extends HttpServlet {

    PassClient passClient = PassClientFactory.getPassClient();
    PassJsonAdapter json = new PassJsonAdapterBasic();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        StringBuffer stringBuffer = new StringBuffer();
        String line;

        BufferedReader reader = request.getReader();
        while ((line = reader.readLine()) != null) {
            stringBuffer.append(line);
        }

        //take the json from crossref and create a Journal
        Journal journal = buildPassJournal(stringBuffer.toString());
        //and compare it with what we already have, update if necessary
        journal = updateJournalinPass(journal);

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        rewriteUri(journal, request);

        try (OutputStream out = response.getOutputStream()) {
            out.write(json.toJson(journal, true));
            response.setStatus(200);
        }


        // LOG.debug("Servicing new request");

    }

    /**
     * Takes JSON which represents journal article metadata from Crossref
     * and populates a new Journal object. Currently we take typed issns and the journal
     * name.
     * @param jsonInput - the JSON metadata from Crossref
     * @return the PASS journal object
     */
    Journal buildPassJournal(String jsonInput) {

        final String XREF_MESSAGE = "message";
        final String XREF_TITLE = "container-title";
        final String XREF_ISSN_TYPE_ARRAY = "issn-type";
        final String XREF_ISSN_TYPE = "type";
        final String XREF_ISSN_VALUE = "value";


        Journal  passJournal = new Journal();

        JsonReader jsonReader;
        jsonReader = Json.createReader(new StringReader(jsonInput));
        JsonObject crossrefMetadata = jsonReader.readObject();
        JsonObject messageObject = crossrefMetadata.getJsonObject(XREF_MESSAGE);
        JsonArray containerTitleArray = messageObject.getJsonArray(XREF_TITLE);
        JsonArray issnTypeArray = messageObject.getJsonArray(XREF_ISSN_TYPE_ARRAY);

        if (!containerTitleArray.isNull(0)) {
            passJournal.setName(containerTitleArray.getString(0));
        }

        for (int i=0; i < issnTypeArray.size(); i++) {
            JsonObject issn = issnTypeArray.getJsonObject(i);

            String type="";

            //translate crossref type strings to PASS type strings
            if (IssnType.PRINT.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                type = IssnType.PRINT.getPassTypeString();
            } else if (IssnType.ELECTRONIC.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                type = IssnType.ELECTRONIC.getPassTypeString();
            }

            //collect the value for this issn
            String value = issn.getString(XREF_ISSN_VALUE);

            if (value.length() > 0) {
                passJournal.getIssns().add(String.join(":", type, value));
            }

        }

        return passJournal;
    }

    /**
     * Take a Journal object constructed from Crossref metadata, and compare it with the
     * version of this object which we have in pass. Construct the most complete Journal
     * object possible from the two sources - PASS objects are more authoritative. Use the
     * Crossref version if we don't have it already in PASS. Store the resulting object in PASS.
     * @param journal - the Journal object generated from Crossref metadata
     * @return the updated Journal object, stored in PASS if the PASS object needs updating.
     */
    Journal updateJournalinPass(Journal journal) {
        List<String> issns = journal.getIssns();

        URI passJournalUri = null;
        Journal passJournal;

        for (String issn : issns) {
            passJournalUri = passClient.findByAttribute(Journal.class, "issns", issn);
            if (passJournalUri != null) {
                break;
            }
        }

        if (passJournalUri == null) {//we don't have this journal in pass yet - let's put this one in
            passJournal = passClient.createAndReadResource(journal, Journal.class);
        } else { //we have a journal, let's see if we can add anything new - title or issns. we add only if not present
            boolean update = false;
            passJournal = passClient.readResource(passJournalUri, Journal.class);
            if (passJournal != null) {

                //check to see if we can supply a journal name
                if ((passJournal.getName() == null || passJournal.getName().isEmpty()) && !journal.getName().isEmpty()) {
                    passJournal.setName(journal.getName());
                    update = true;
                }

                //check to see if we can supply issns
                if (!passJournal.getIssns().containsAll(journal.getIssns())) {
                    List<String> newIssnList = Stream.concat(passJournal.getIssns().stream(), journal.getIssns().stream()).distinct().collect(Collectors.toList());
                    passJournal.setIssns(newIssnList);
                    update = true;
                }

                if (update) {
                    passClient.updateResource(passJournal);
                }
            } else {
                throw new RuntimeException("URI for journal was found, but the object could not be retrieved.");
            }

        }
        return passJournal;
    }

    private void rewriteUri(Journal journal, HttpServletRequest request) {

        final Protocol proto = Protocol.of(request, journal.getId());
        final Host host = Host.of(request, journal.getId());

        final URI u = journal.getId();

        try {
            journal.setId(new URI(
                    proto.get(),
                    u.getUserInfo(),
                    host.getHost(),
                    host.getPort(),
                    u.getPath(),
                    u.getQuery(),
                    u.getFragment()));
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Error rewriting URI " + journal.getId());
        }

    }

    private static class Host {

        final String host;

        final int port;

        static Host of(HttpServletRequest request, URI defaults) {
            final String host = request.getHeader("host");
            if (host != null && host != "") {
                return new Host(host);
            } else {
                if (request.getRequestURL() != null) {
                    return new Host(URI.create(request.getRequestURL().toString()).getHost());
                } else {
                    return new Host(defaults.getHost(), defaults.getPort());
                }
            }
        }

        private Host(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private Host(String hostname) {
            if (hostname.contains(":")) {
                final String[] parts = hostname.split(":");
                host = parts[0];
                port = Integer.valueOf(parts[1]);
            } else {
                host = hostname;
                port = -1;
            }
        }

        String getHost() {
            return host;
        }

        int getPort() {
            return port;
        }
    }

    private static class Protocol {

        final String proto;

        static Protocol of(HttpServletRequest request, URI defaults) {
            if (request.getHeader("X-Forwarded-Proto") != null) {
                return new Protocol(request.getHeader("X-Forwarded-Proto"));
            } else if (request.getRequestURL() != null) {
                return new Protocol(URI.create(request.getRequestURL().toString()).getScheme());
            } else {
                return new Protocol(defaults.getScheme());
            }
        }

        private Protocol(String proto) {
            this.proto = proto;
        }

        String get() {
            return proto;
        }
    }

}
