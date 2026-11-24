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
package io.openepcis.service.context;

import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.exception.NotAcceptedException;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class DefaultEPCISContext implements EPCISContext {
    protected String contentType;
    protected EPCISVersion epcisVersion;
    protected EPCISFormat epcisFormat;
    protected final AtomicReference<Optional<String>> gs1Extensions = new AtomicReference<>(Optional.empty());
    protected EPCISVersion epcisVersionMin = EPCISVersion.VERSION_1_2_0;
    protected EPCISVersion epcisVersionMax = EPCISVersion.VERSION_2_0_1;
    protected CBVVersion cbvVersion = CBVVersion.VERSION_2_0_0;
    protected CBVVersion cbvVersionMin = CBVVersion.VERSION_1_2_2;
    protected CBVVersion cbvVersionMax = CBVVersion.VERSION_2_0_1;
    protected FormatPreference formatPreference = FormatPreference.getInstance(EPCFormat.No_Preference, CBVFormat.No_Preference);

    public DefaultEPCISContext(EPCISContext ctx) {
        this(ctx.getContentType(), ctx.getEpcisVersion(), ctx.getEpcisFormat(), ctx.getEpcisVersionMin(), ctx.getEpcisVersionMax(), ctx.getCbvVersion(), ctx.getCbvVersionMin(), ctx.getCbvVersionMax(), ctx.getFormatPreference());
    }

    public DefaultEPCISContext(String contentType, EPCISVersion epcisVersion, EPCISFormat epcisFormat, EPCISVersion epcisVersionMin, EPCISVersion epcisVersionMax, CBVVersion cbvVersion, CBVVersion cbvVersionMin, CBVVersion cbvVersionMax, FormatPreference formatPreference) {
        this.contentType = contentType;
        this.epcisVersion = epcisVersion;
        this.epcisFormat = epcisFormat;
        this.epcisVersionMin = epcisVersionMin;
        this.epcisVersionMax = epcisVersionMax;
        this.cbvVersion = cbvVersion;
        this.cbvVersionMin = cbvVersionMin;
        this.cbvVersionMax = cbvVersionMax;
        this.formatPreference = formatPreference;
    }

    public DefaultEPCISContext(Optional<String> epcisVersion, Optional<String> epcisVersionMin, Optional<String> epcisVersionMax, Optional<String> cbvVersion, Optional<String> cbvVersionMin, Optional<String> cbvVersionMax, Optional<String> epcFormat, Optional<String> cbvFormat) {
        this(EPCISFormat.JSON_LD.getMediaType(), epcisVersion, epcisVersionMin, epcisVersionMax, cbvVersion, cbvVersionMin, cbvVersionMax, epcFormat, cbvFormat);
    }

    public DefaultEPCISContext(String contentType, Optional<String> epcisVersion, Optional<String> epcisVersionMin, Optional<String> epcisVersionMax, Optional<String> cbvVersion, Optional<String> cbvVersionMin, Optional<String> cbvVersionMax, Optional<String> epcFormat, Optional<String> cbvFormat) {
        this.contentType = contentType;
        this.epcisFormat = EPCISFormat.fromString(contentType).orElse(EPCISFormat.JSON_LD);
        // EPCIS versions
        this.epcisVersion = parseRequired(epcisVersion.orElse(EPCISVersion.VERSION_2_0_0.getVersion()), EPCISVersion::fromString, "epcisVersion");
        this.epcisVersionMin = parseOptional(epcisVersionMin.orElse(null), EPCISVersion::fromString, "epcisVersionMin");
        this.epcisVersionMax = parseOptional(epcisVersionMax.orElse(null), EPCISVersion::fromString, "epcisVersionMax");
        // CBV versions
        this.cbvVersion = parseRequired(cbvVersion.orElse(CBVVersion.VERSION_2_0_0.getVersion()), CBVVersion::fromString, "cbvVersion");
        this.cbvVersionMin = parseOptional(cbvVersionMin.orElse(null), CBVVersion::fromString, "cbvVersionMin");
        this.cbvVersionMax = parseOptional(cbvVersionMax.orElse(null), CBVVersion::fromString, "cbvVersionMax");
        this.formatPreference = FormatPreference.getInstance(epcFormat, cbvFormat);
    }

    private <T> T parseRequired(String value, Function<String, Optional<T>> parser, String fieldName) {
        return parser.apply(value).orElseThrow(() -> new NotAcceptedException("Unsupported value for header \'" + fieldName + "\': " + value));
    }

    private <T> T parseOptional(String value, Function<String, Optional<T>> parser, String fieldName) {
        if (value == null || value.isBlank()) return null;
        return parser.apply(value).orElseThrow(() -> new NotAcceptedException("Unsupported value for header \'" + fieldName + "\': " + value));
    }

    public DefaultEPCISContext(final String contentType, final EPCISVersion version) {
        this.contentType = contentType;
        this.epcisFormat = EPCISFormat.fromString(contentType).orElse(EPCISFormat.JSON_LD);
        this.epcisVersion = version;
    }

    public String getContentType() {
        return this.contentType;
    }

    public EPCISVersion getEpcisVersion() {
        return this.epcisVersion;
    }

    public EPCISFormat getEpcisFormat() {
        return this.epcisFormat;
    }

    public AtomicReference<Optional<String>> getGs1Extensions() {
        return this.gs1Extensions;
    }

    public EPCISVersion getEpcisVersionMin() {
        return this.epcisVersionMin;
    }

    public EPCISVersion getEpcisVersionMax() {
        return this.epcisVersionMax;
    }

    public CBVVersion getCbvVersion() {
        return this.cbvVersion;
    }

    public CBVVersion getCbvVersionMin() {
        return this.cbvVersionMin;
    }

    public CBVVersion getCbvVersionMax() {
        return this.cbvVersionMax;
    }

    public FormatPreference getFormatPreference() {
        return this.formatPreference;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setEpcisVersion(EPCISVersion epcisVersion) {
        this.epcisVersion = epcisVersion;
    }

    public void setEpcisFormat(EPCISFormat epcisFormat) {
        this.epcisFormat = epcisFormat;
    }

    public void setEpcisVersionMin(EPCISVersion epcisVersionMin) {
        this.epcisVersionMin = epcisVersionMin;
    }

    public void setEpcisVersionMax(EPCISVersion epcisVersionMax) {
        this.epcisVersionMax = epcisVersionMax;
    }

    public void setCbvVersion(CBVVersion cbvVersion) {
        this.cbvVersion = cbvVersion;
    }

    public void setCbvVersionMin(CBVVersion cbvVersionMin) {
        this.cbvVersionMin = cbvVersionMin;
    }

    public void setCbvVersionMax(CBVVersion cbvVersionMax) {
        this.cbvVersionMax = cbvVersionMax;
    }

    public void setFormatPreference(FormatPreference formatPreference) {
        this.formatPreference = formatPreference;
    }

    @Override
    public String toString() {
        return "DefaultEPCISContext(contentType=" + this.getContentType() + ", epcisVersion=" + this.getEpcisVersion() + ", epcisFormat=" + this.getEpcisFormat() + ", gs1Extensions=" + this.getGs1Extensions() + ", epcisVersionMin=" + this.getEpcisVersionMin() + ", epcisVersionMax=" + this.getEpcisVersionMax() + ", cbvVersion=" + this.getCbvVersion() + ", cbvVersionMin=" + this.getCbvVersionMin() + ", cbvVersionMax=" + this.getCbvVersionMax() + ", formatPreference=" + this.getFormatPreference() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DefaultEPCISContext)) return false;
        DefaultEPCISContext other = (DefaultEPCISContext) o;
        if (!other.canEqual((Object) this)) return false;
        Object this$contentType = this.getContentType();
        Object other$contentType = other.getContentType();
        if (this$contentType == null ? other$contentType != null : !this$contentType.equals(other$contentType)) return false;
        Object this$epcisVersion = this.getEpcisVersion();
        Object other$epcisVersion = other.getEpcisVersion();
        if (this$epcisVersion == null ? other$epcisVersion != null : !this$epcisVersion.equals(other$epcisVersion)) return false;
        Object this$epcisFormat = this.getEpcisFormat();
        Object other$epcisFormat = other.getEpcisFormat();
        if (this$epcisFormat == null ? other$epcisFormat != null : !this$epcisFormat.equals(other$epcisFormat)) return false;
        Object this$gs1Extensions = this.getGs1Extensions();
        Object other$gs1Extensions = other.getGs1Extensions();
        if (this$gs1Extensions == null ? other$gs1Extensions != null : !this$gs1Extensions.equals(other$gs1Extensions)) return false;
        Object this$epcisVersionMin = this.getEpcisVersionMin();
        Object other$epcisVersionMin = other.getEpcisVersionMin();
        if (this$epcisVersionMin == null ? other$epcisVersionMin != null : !this$epcisVersionMin.equals(other$epcisVersionMin)) return false;
        Object this$epcisVersionMax = this.getEpcisVersionMax();
        Object other$epcisVersionMax = other.getEpcisVersionMax();
        if (this$epcisVersionMax == null ? other$epcisVersionMax != null : !this$epcisVersionMax.equals(other$epcisVersionMax)) return false;
        Object this$cbvVersion = this.getCbvVersion();
        Object other$cbvVersion = other.getCbvVersion();
        if (this$cbvVersion == null ? other$cbvVersion != null : !this$cbvVersion.equals(other$cbvVersion)) return false;
        Object this$cbvVersionMin = this.getCbvVersionMin();
        Object other$cbvVersionMin = other.getCbvVersionMin();
        if (this$cbvVersionMin == null ? other$cbvVersionMin != null : !this$cbvVersionMin.equals(other$cbvVersionMin)) return false;
        Object this$cbvVersionMax = this.getCbvVersionMax();
        Object other$cbvVersionMax = other.getCbvVersionMax();
        if (this$cbvVersionMax == null ? other$cbvVersionMax != null : !this$cbvVersionMax.equals(other$cbvVersionMax)) return false;
        Object this$formatPreference = this.getFormatPreference();
        Object other$formatPreference = other.getFormatPreference();
        if (this$formatPreference == null ? other$formatPreference != null : !this$formatPreference.equals(other$formatPreference)) return false;
        return true;
    }

    protected boolean canEqual(Object other) {
        return other instanceof DefaultEPCISContext;
    }

    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $contentType = this.getContentType();
        result = result * PRIME + ($contentType == null ? 43 : $contentType.hashCode());
        Object $epcisVersion = this.getEpcisVersion();
        result = result * PRIME + ($epcisVersion == null ? 43 : $epcisVersion.hashCode());
        Object $epcisFormat = this.getEpcisFormat();
        result = result * PRIME + ($epcisFormat == null ? 43 : $epcisFormat.hashCode());
        Object $gs1Extensions = this.getGs1Extensions();
        result = result * PRIME + ($gs1Extensions == null ? 43 : $gs1Extensions.hashCode());
        Object $epcisVersionMin = this.getEpcisVersionMin();
        result = result * PRIME + ($epcisVersionMin == null ? 43 : $epcisVersionMin.hashCode());
        Object $epcisVersionMax = this.getEpcisVersionMax();
        result = result * PRIME + ($epcisVersionMax == null ? 43 : $epcisVersionMax.hashCode());
        Object $cbvVersion = this.getCbvVersion();
        result = result * PRIME + ($cbvVersion == null ? 43 : $cbvVersion.hashCode());
        Object $cbvVersionMin = this.getCbvVersionMin();
        result = result * PRIME + ($cbvVersionMin == null ? 43 : $cbvVersionMin.hashCode());
        Object $cbvVersionMax = this.getCbvVersionMax();
        result = result * PRIME + ($cbvVersionMax == null ? 43 : $cbvVersionMax.hashCode());
        Object $formatPreference = this.getFormatPreference();
        result = result * PRIME + ($formatPreference == null ? 43 : $formatPreference.hashCode());
        return result;
    }

    public DefaultEPCISContext() {
    }
}
