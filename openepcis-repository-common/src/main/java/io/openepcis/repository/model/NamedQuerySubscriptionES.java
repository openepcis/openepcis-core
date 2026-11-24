/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.repository.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.Schedule;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamedQuerySubscriptionES {
    private UUID subscriptionID;
    private String wsSessionID;
    private String queryName;
    private URI callbackUrl;
    private URI dest;
    private String signatureToken;
    private String secret;
    private OffsetDateTime minRecordTime;
    private OffsetDateTime initialRecordTime;
    private Boolean reportIfEmpty;
    private Boolean stream;
    private Schedule schedule;
    private boolean deleted;
    private OffsetDateTime createdAt;
    private String epcFormat;
    private String cbvFormat;
    private String status;
    private String errorDescription;
    private String subscribedBy;
    private MetadataES metadata;
    private String epcisVersion;

    public NamedQuerySubscriptionES(NamedQuerySubscription namedQuerySubscription) {
        this.subscriptionID = namedQuerySubscription.getSubscriptionID();
        this.wsSessionID = namedQuerySubscription.getWsSessionID();
        this.queryName = namedQuerySubscription.getQueryName();
        this.epcisVersion = namedQuerySubscription.getEpcisVersion();
        this.minRecordTime = namedQuerySubscription.getMinRecordTime();
        this.initialRecordTime = namedQuerySubscription.getInitialRecordTime();
        this.reportIfEmpty = namedQuerySubscription.getReportIfEmpty();
        this.stream = namedQuerySubscription.getStream();
        this.schedule = namedQuerySubscription.getSchedule();
        this.deleted = namedQuerySubscription.getDeleted();
        this.createdAt = namedQuerySubscription.getCreatedAt();
        this.epcFormat = namedQuerySubscription.getEpcFormat();
        this.cbvFormat = namedQuerySubscription.getCbvFormat();
        this.subscribedBy = namedQuerySubscription.getSubscribedBy();
        if (Objects.nonNull(namedQuerySubscription.getDest())) {
            this.dest = namedQuerySubscription.getDest();
        }
        if (Objects.nonNull(namedQuerySubscription.getSignatureToken())) {
            this.signatureToken = namedQuerySubscription.getSignatureToken();
        }
    }

    public static NamedQuerySubscription mapToNamedQuerySubscription(NamedQuerySubscriptionES es) {
        return NamedQuerySubscription.builder().subscriptionID(es.getSubscriptionID()).wsSessionID(es.getWsSessionID()).queryName(es.getQueryName()).epcisVersion(es.epcisVersion).dest(es.getDest()).signatureToken(es.getSignatureToken()).minRecordTime(es.getMinRecordTime()).initialRecordTime(es.getInitialRecordTime()).reportIfEmpty(es.getReportIfEmpty()).stream(es.getStream()).schedule(es.getSchedule()).deleted(es.isDeleted()).createdAt(es.getCreatedAt()).epcFormat(es.getEpcFormat()).cbvFormat(es.getCbvFormat()).subscribedBy(es.getSubscribedBy()).defaultGroup(es.getMetadata().getCapturedBy().get(0).getDefaultGroup()).build();
    }

    private static boolean $default$deleted() {
        return false;
    }


    public static class NamedQuerySubscriptionESBuilder {
        private UUID subscriptionID;
        private String wsSessionID;
        private String queryName;
        private URI callbackUrl;
        private URI dest;
        private String signatureToken;
        private String secret;
        private OffsetDateTime minRecordTime;
        private OffsetDateTime initialRecordTime;
        private Boolean reportIfEmpty;
        private Boolean stream;
        private Schedule schedule;
        private boolean deleted$set;
        private boolean deleted$value;
        private OffsetDateTime createdAt;
        private String epcFormat;
        private String cbvFormat;
        private String status;
        private String errorDescription;
        private String subscribedBy;
        private MetadataES metadata;
        private String epcisVersion;

        NamedQuerySubscriptionESBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder subscriptionID(UUID subscriptionID) {
            this.subscriptionID = subscriptionID;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder wsSessionID(String wsSessionID) {
            this.wsSessionID = wsSessionID;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder queryName(String queryName) {
            this.queryName = queryName;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder callbackUrl(URI callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder dest(URI dest) {
            this.dest = dest;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder signatureToken(String signatureToken) {
            this.signatureToken = signatureToken;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder secret(String secret) {
            this.secret = secret;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder minRecordTime(OffsetDateTime minRecordTime) {
            this.minRecordTime = minRecordTime;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder initialRecordTime(OffsetDateTime initialRecordTime) {
            this.initialRecordTime = initialRecordTime;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder reportIfEmpty(Boolean reportIfEmpty) {
            this.reportIfEmpty = reportIfEmpty;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder schedule(Schedule schedule) {
            this.schedule = schedule;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder deleted(boolean deleted) {
            this.deleted$value = deleted;
            deleted$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder createdAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder epcFormat(String epcFormat) {
            this.epcFormat = epcFormat;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder cbvFormat(String cbvFormat) {
            this.cbvFormat = cbvFormat;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder status(String status) {
            this.status = status;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder errorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder subscribedBy(String subscribedBy) {
            this.subscribedBy = subscribedBy;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder metadata(MetadataES metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder epcisVersion(String epcisVersion) {
            this.epcisVersion = epcisVersion;
            return this;
        }

        public NamedQuerySubscriptionES build() {
            boolean deleted$value = this.deleted$value;
            if (!this.deleted$set) deleted$value = NamedQuerySubscriptionES.$default$deleted();
            return new NamedQuerySubscriptionES(this.subscriptionID, this.wsSessionID, this.queryName, this.callbackUrl, this.dest, this.signatureToken, this.secret, this.minRecordTime, this.initialRecordTime, this.reportIfEmpty, this.stream, this.schedule, deleted$value, this.createdAt, this.epcFormat, this.cbvFormat, this.status, this.errorDescription, this.subscribedBy, this.metadata, this.epcisVersion);
        }

        @Override
        public String toString() {
            return "NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder(subscriptionID=" + this.subscriptionID + ", wsSessionID=" + this.wsSessionID + ", queryName=" + this.queryName + ", callbackUrl=" + this.callbackUrl + ", dest=" + this.dest + ", signatureToken=" + this.signatureToken + ", secret=" + this.secret + ", minRecordTime=" + this.minRecordTime + ", initialRecordTime=" + this.initialRecordTime + ", reportIfEmpty=" + this.reportIfEmpty + ", stream=" + this.stream + ", schedule=" + this.schedule + ", deleted$value=" + this.deleted$value + ", createdAt=" + this.createdAt + ", epcFormat=" + this.epcFormat + ", cbvFormat=" + this.cbvFormat + ", status=" + this.status + ", errorDescription=" + this.errorDescription + ", subscribedBy=" + this.subscribedBy + ", metadata=" + this.metadata + ", epcisVersion=" + this.epcisVersion + ")";
        }
    }

    public static NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder builder() {
        return new NamedQuerySubscriptionES.NamedQuerySubscriptionESBuilder();
    }

    public UUID getSubscriptionID() {
        return this.subscriptionID;
    }

    public String getWsSessionID() {
        return this.wsSessionID;
    }

    public String getQueryName() {
        return this.queryName;
    }

    public URI getCallbackUrl() {
        return this.callbackUrl;
    }

    public URI getDest() {
        return this.dest;
    }

    public String getSignatureToken() {
        return this.signatureToken;
    }

    public String getSecret() {
        return this.secret;
    }

    public OffsetDateTime getMinRecordTime() {
        return this.minRecordTime;
    }

    public OffsetDateTime getInitialRecordTime() {
        return this.initialRecordTime;
    }

    public Boolean getReportIfEmpty() {
        return this.reportIfEmpty;
    }

    public Boolean getStream() {
        return this.stream;
    }

    public Schedule getSchedule() {
        return this.schedule;
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    public String getEpcFormat() {
        return this.epcFormat;
    }

    public String getCbvFormat() {
        return this.cbvFormat;
    }

    public String getStatus() {
        return this.status;
    }

    public String getErrorDescription() {
        return this.errorDescription;
    }

    public String getSubscribedBy() {
        return this.subscribedBy;
    }

    public MetadataES getMetadata() {
        return this.metadata;
    }

    public String getEpcisVersion() {
        return this.epcisVersion;
    }

    public void setSubscriptionID(UUID subscriptionID) {
        this.subscriptionID = subscriptionID;
    }

    public void setWsSessionID(String wsSessionID) {
        this.wsSessionID = wsSessionID;
    }

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }

    public void setCallbackUrl(URI callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public void setDest(URI dest) {
        this.dest = dest;
    }

    public void setSignatureToken(String signatureToken) {
        this.signatureToken = signatureToken;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setMinRecordTime(OffsetDateTime minRecordTime) {
        this.minRecordTime = minRecordTime;
    }

    public void setInitialRecordTime(OffsetDateTime initialRecordTime) {
        this.initialRecordTime = initialRecordTime;
    }

    public void setReportIfEmpty(Boolean reportIfEmpty) {
        this.reportIfEmpty = reportIfEmpty;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setEpcFormat(String epcFormat) {
        this.epcFormat = epcFormat;
    }

    public void setCbvFormat(String cbvFormat) {
        this.cbvFormat = cbvFormat;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public void setSubscribedBy(String subscribedBy) {
        this.subscribedBy = subscribedBy;
    }

    public void setMetadata(MetadataES metadata) {
        this.metadata = metadata;
    }

    public void setEpcisVersion(String epcisVersion) {
        this.epcisVersion = epcisVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof NamedQuerySubscriptionES)) return false;
        NamedQuerySubscriptionES other = (NamedQuerySubscriptionES) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.isDeleted() != other.isDeleted()) return false;
        Object this$reportIfEmpty = this.getReportIfEmpty();
        Object other$reportIfEmpty = other.getReportIfEmpty();
        if (this$reportIfEmpty == null ? other$reportIfEmpty != null : !this$reportIfEmpty.equals(other$reportIfEmpty)) return false;
        Object this$stream = this.getStream();
        Object other$stream = other.getStream();
        if (this$stream == null ? other$stream != null : !this$stream.equals(other$stream)) return false;
        Object this$subscriptionID = this.getSubscriptionID();
        Object other$subscriptionID = other.getSubscriptionID();
        if (this$subscriptionID == null ? other$subscriptionID != null : !this$subscriptionID.equals(other$subscriptionID)) return false;
        Object this$wsSessionID = this.getWsSessionID();
        Object other$wsSessionID = other.getWsSessionID();
        if (this$wsSessionID == null ? other$wsSessionID != null : !this$wsSessionID.equals(other$wsSessionID)) return false;
        Object this$queryName = this.getQueryName();
        Object other$queryName = other.getQueryName();
        if (this$queryName == null ? other$queryName != null : !this$queryName.equals(other$queryName)) return false;
        Object this$callbackUrl = this.getCallbackUrl();
        Object other$callbackUrl = other.getCallbackUrl();
        if (this$callbackUrl == null ? other$callbackUrl != null : !this$callbackUrl.equals(other$callbackUrl)) return false;
        Object this$dest = this.getDest();
        Object other$dest = other.getDest();
        if (this$dest == null ? other$dest != null : !this$dest.equals(other$dest)) return false;
        Object this$signatureToken = this.getSignatureToken();
        Object other$signatureToken = other.getSignatureToken();
        if (this$signatureToken == null ? other$signatureToken != null : !this$signatureToken.equals(other$signatureToken)) return false;
        Object this$secret = this.getSecret();
        Object other$secret = other.getSecret();
        if (this$secret == null ? other$secret != null : !this$secret.equals(other$secret)) return false;
        Object this$minRecordTime = this.getMinRecordTime();
        Object other$minRecordTime = other.getMinRecordTime();
        if (this$minRecordTime == null ? other$minRecordTime != null : !this$minRecordTime.equals(other$minRecordTime)) return false;
        Object this$initialRecordTime = this.getInitialRecordTime();
        Object other$initialRecordTime = other.getInitialRecordTime();
        if (this$initialRecordTime == null ? other$initialRecordTime != null : !this$initialRecordTime.equals(other$initialRecordTime)) return false;
        Object this$schedule = this.getSchedule();
        Object other$schedule = other.getSchedule();
        if (this$schedule == null ? other$schedule != null : !this$schedule.equals(other$schedule)) return false;
        Object this$createdAt = this.getCreatedAt();
        Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        Object this$epcFormat = this.getEpcFormat();
        Object other$epcFormat = other.getEpcFormat();
        if (this$epcFormat == null ? other$epcFormat != null : !this$epcFormat.equals(other$epcFormat)) return false;
        Object this$cbvFormat = this.getCbvFormat();
        Object other$cbvFormat = other.getCbvFormat();
        if (this$cbvFormat == null ? other$cbvFormat != null : !this$cbvFormat.equals(other$cbvFormat)) return false;
        Object this$status = this.getStatus();
        Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        Object this$errorDescription = this.getErrorDescription();
        Object other$errorDescription = other.getErrorDescription();
        if (this$errorDescription == null ? other$errorDescription != null : !this$errorDescription.equals(other$errorDescription)) return false;
        Object this$subscribedBy = this.getSubscribedBy();
        Object other$subscribedBy = other.getSubscribedBy();
        if (this$subscribedBy == null ? other$subscribedBy != null : !this$subscribedBy.equals(other$subscribedBy)) return false;
        Object this$metadata = this.getMetadata();
        Object other$metadata = other.getMetadata();
        if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
        Object this$epcisVersion = this.getEpcisVersion();
        Object other$epcisVersion = other.getEpcisVersion();
        if (this$epcisVersion == null ? other$epcisVersion != null : !this$epcisVersion.equals(other$epcisVersion)) return false;
        return true;
    }

    protected boolean canEqual(Object other) {
        return other instanceof NamedQuerySubscriptionES;
    }

    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isDeleted() ? 79 : 97);
        Object $reportIfEmpty = this.getReportIfEmpty();
        result = result * PRIME + ($reportIfEmpty == null ? 43 : $reportIfEmpty.hashCode());
        Object $stream = this.getStream();
        result = result * PRIME + ($stream == null ? 43 : $stream.hashCode());
        Object $subscriptionID = this.getSubscriptionID();
        result = result * PRIME + ($subscriptionID == null ? 43 : $subscriptionID.hashCode());
        Object $wsSessionID = this.getWsSessionID();
        result = result * PRIME + ($wsSessionID == null ? 43 : $wsSessionID.hashCode());
        Object $queryName = this.getQueryName();
        result = result * PRIME + ($queryName == null ? 43 : $queryName.hashCode());
        Object $callbackUrl = this.getCallbackUrl();
        result = result * PRIME + ($callbackUrl == null ? 43 : $callbackUrl.hashCode());
        Object $dest = this.getDest();
        result = result * PRIME + ($dest == null ? 43 : $dest.hashCode());
        Object $signatureToken = this.getSignatureToken();
        result = result * PRIME + ($signatureToken == null ? 43 : $signatureToken.hashCode());
        Object $secret = this.getSecret();
        result = result * PRIME + ($secret == null ? 43 : $secret.hashCode());
        Object $minRecordTime = this.getMinRecordTime();
        result = result * PRIME + ($minRecordTime == null ? 43 : $minRecordTime.hashCode());
        Object $initialRecordTime = this.getInitialRecordTime();
        result = result * PRIME + ($initialRecordTime == null ? 43 : $initialRecordTime.hashCode());
        Object $schedule = this.getSchedule();
        result = result * PRIME + ($schedule == null ? 43 : $schedule.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $epcFormat = this.getEpcFormat();
        result = result * PRIME + ($epcFormat == null ? 43 : $epcFormat.hashCode());
        Object $cbvFormat = this.getCbvFormat();
        result = result * PRIME + ($cbvFormat == null ? 43 : $cbvFormat.hashCode());
        Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        Object $errorDescription = this.getErrorDescription();
        result = result * PRIME + ($errorDescription == null ? 43 : $errorDescription.hashCode());
        Object $subscribedBy = this.getSubscribedBy();
        result = result * PRIME + ($subscribedBy == null ? 43 : $subscribedBy.hashCode());
        Object $metadata = this.getMetadata();
        result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
        Object $epcisVersion = this.getEpcisVersion();
        result = result * PRIME + ($epcisVersion == null ? 43 : $epcisVersion.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "NamedQuerySubscriptionES(subscriptionID=" + this.getSubscriptionID() + ", wsSessionID=" + this.getWsSessionID() + ", queryName=" + this.getQueryName() + ", callbackUrl=" + this.getCallbackUrl() + ", dest=" + this.getDest() + ", signatureToken=" + this.getSignatureToken() + ", secret=" + this.getSecret() + ", minRecordTime=" + this.getMinRecordTime() + ", initialRecordTime=" + this.getInitialRecordTime() + ", reportIfEmpty=" + this.getReportIfEmpty() + ", stream=" + this.getStream() + ", schedule=" + this.getSchedule() + ", deleted=" + this.isDeleted() + ", createdAt=" + this.getCreatedAt() + ", epcFormat=" + this.getEpcFormat() + ", cbvFormat=" + this.getCbvFormat() + ", status=" + this.getStatus() + ", errorDescription=" + this.getErrorDescription() + ", subscribedBy=" + this.getSubscribedBy() + ", metadata=" + this.getMetadata() + ", epcisVersion=" + this.getEpcisVersion() + ")";
    }

    public NamedQuerySubscriptionES() {
        this.deleted = NamedQuerySubscriptionES.$default$deleted();
    }

    public NamedQuerySubscriptionES(UUID subscriptionID, String wsSessionID, String queryName, URI callbackUrl, URI dest, String signatureToken, String secret, OffsetDateTime minRecordTime, OffsetDateTime initialRecordTime, Boolean reportIfEmpty, Boolean stream, Schedule schedule, boolean deleted, OffsetDateTime createdAt, String epcFormat, String cbvFormat, String status, String errorDescription, String subscribedBy, MetadataES metadata, String epcisVersion) {
        this.subscriptionID = subscriptionID;
        this.wsSessionID = wsSessionID;
        this.queryName = queryName;
        this.callbackUrl = callbackUrl;
        this.dest = dest;
        this.signatureToken = signatureToken;
        this.secret = secret;
        this.minRecordTime = minRecordTime;
        this.initialRecordTime = initialRecordTime;
        this.reportIfEmpty = reportIfEmpty;
        this.stream = stream;
        this.schedule = schedule;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.epcFormat = epcFormat;
        this.cbvFormat = cbvFormat;
        this.status = status;
        this.errorDescription = errorDescription;
        this.subscribedBy = subscribedBy;
        this.metadata = metadata;
        this.epcisVersion = epcisVersion;
    }
}
