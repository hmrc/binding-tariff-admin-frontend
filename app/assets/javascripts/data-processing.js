var dataProcessing = {

    checkStatus: function initiate(url) {

        return new Promise(function (resolve, reject) {
            $.ajax({
                type: "GET",
                url: url,
                processData: false,
                contentType: false,
                mimeType: "application/json",
                success: function (response) {
                    resolve({
                        status: response.status.value,
                        migratedBtiCount: response.status.migratedBtiCount,
                        migratedLiabilityCount: response.status.migratedLiabilityCount,
                        errorCount: response.status.errorCount,
                        discardedBtiCount: response.status.discardedBtiCount,
                        discardedLiabilityCount: response.status.discardedLiabilityCount,
                        discardReasons: response.status.discardReasons,
                        errors: response.status.errors
                    });
                },
                error: function (response) {
                    reject({
                        status: response.error
                    });
                }
            })
        });
    },

    updateStatus: function upload(url) {
                getStatus();

                function getStatus() {

                    dataProcessing.checkStatus(url)
                        .then(function (response) {
                            updateState(response.status);
                            updateContinueMessage();

                            updateMigratedBtiCount(response.migratedBtiCount);
                            updateMigratedLiabilityCount(response.migratedLiabilityCount);
                            updateDiscardedBtiCount(response.discardedBtiCount);
                            updateDiscardedLiabilityCount(response.discardedLiabilityCount);

                            updateErrorCount(response.errorCount);
                            updateErrors(response.errors);

                            updateDiscardedBtiReasons(response.discardReasons);
                            updateDiscardedLiabilityReasons(response.discardReasons);
                        })
                        .catch(function (response) {
                            updateError(response.error);
                        })

                }

                function updateState(status) {
                    if (status !== undefined) {
                        document.getElementById("bti-processing-status").innerHTML = status;
                        document.getElementById("liabilities-processing-status").innerHTML = status;
                    }
                }

                function updateErrorCount(errorCount) {
                    if(errorCount > 0) {

                        document.getElementById("error-count").innerHTML = errorCount;
                        document.getElementById("error-row").removeAttribute("aria-hidden");
                        document.getElementById("error-row").removeAttribute("hidden");
                        document.getElementById("summary-container").removeAttribute("aria-hidden");
                        document.getElementById("summary-container").removeAttribute("hidden");
                    }
                }

                function updateErrors(errors) {
                    if (errors === undefined || errors.length === 0) {
                        return;
                    }

                    var listHtml = "";
                    errors.forEach(function(error) {
                        listHtml += "<li class=\"font-small\"><span class=\"error-message\">" + error + "</span></li>";
                    });

                    document.getElementById("error-list").innerHTML = listHtml;
                    document.getElementById("error-container").removeAttribute("aria-hidden");
                    document.getElementById("error-container").removeAttribute("hidden");
                }

                function updateMigratedBtiCount(migratedBtiCount) {
                    if(migratedBtiCount > 0) {
                        document.getElementById("migrated-bti-count").innerHTML = migratedBtiCount;
                        document.getElementById("summary-container").removeAttribute("aria-hidden");
                        document.getElementById("summary-container").removeAttribute("hidden");
                    }
                }

                function updateMigratedLiabilityCount(migratedLiabilityCount) {
                    if(migratedLiabilityCount > 0) {
                        document.getElementById("migrated-liability-count").innerHTML = migratedLiabilityCount;
                        document.getElementById("summary-container").removeAttribute("aria-hidden");
                        document.getElementById("summary-container").removeAttribute("hidden");
                    }
                }

                function updateDiscardedBtiCount(discardedBtiCount) {
                    if(discardedBtiCount > 0) {

                        document.getElementById("discarded-bti-count").innerHTML = discardedBtiCount;
                        document.getElementById("summary-container").removeAttribute("aria-hidden");
                        document.getElementById("summary-container").removeAttribute("hidden");
                    }
                }

                function updateDiscardedLiabilityCount(discardedLiabilityCount) {
                    if(discardedLiabilityCount > 0) {

                        document.getElementById("discarded-liability-count").innerHTML = discardedLiabilityCount;
                        document.getElementById("summary-container").removeAttribute("aria-hidden");
                        document.getElementById("summary-container").removeAttribute("hidden");
                    }
                }

                function updateDiscardedBtiReasons(discardReasons) {
                    if (discardReasons === undefined || discardReasons.length === 0) {
                        return;
                    }

                    var btiWithHistoricCase = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_HISTORIC_CASE"; });
                    var rejectedBti = discardReasons.filter(function (reason) { return reason.type == "REJECTED_BTI"; });
                    var suppressedBti = discardReasons.filter(function (reason) { return reason.type == "SUPPRESSED_BTI"; });
                    var btiWithNoApplicationRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_APPLICATION_RECORD"; });
                    var btiWithNoCaseBtiRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_CASE_BTI_RECORD"; });
                    var btiWithNoClassMethRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_CLASS_METH_RECORD"; });
                    var btiWithNoAddressRecords = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_ADDRESS_RECORDS"; });

                    updateDiscardedCase(btiWithHistoricCase, "discarded-bti-historic-case");
                    updateDiscardedCase(rejectedBti, "rejected-bti");
                    updateDiscardedCase(suppressedBti, "suppressed-bti");
                    updateDiscardedCase(btiWithNoApplicationRecord, "bti-no-application-record");
                    updateDiscardedCase(btiWithNoCaseBtiRecord, "bti-no-case-bti-record");
                    updateDiscardedCase(btiWithNoClassMethRecord, "bti-no-class-meth-record");
                    updateDiscardedCase(btiWithNoAddressRecords, "bti-no-address-records");

                    if (btiWithHistoricCase.length > 0 ||
                        rejectedBti.length > 0 ||
                        suppressedBti.length > 0 ||
                        btiWithNoApplicationRecord.length > 0 ||
                        btiWithNoCaseBtiRecord.length > 0 ||
                        btiWithNoClassMethRecord.length > 0 ||
                        btiWithNoAddressRecords.length > 0) {
                        document.getElementById("bti-discarded-container").removeAttribute("aria-hidden");
                        document.getElementById("bti-discarded-container").removeAttribute("hidden");
                        document.getElementById("detailed-bti-discarded-cases-container").removeAttribute("aria-hidden");
                        document.getElementById("detailed-bti-discarded-cases-container").removeAttribute("hidden");
                    }
                }

                function updateDiscardedLiabilityReasons(discardReasons) {
                    if (discardReasons === undefined) {
                        return;
                    }

                    var liabilityWithHistoricCase = discardReasons.filter(function (reason) { return reason.type == "LIABILITY_WITH_HISTORIC_CASE"; });
                    var rejectedLiability = discardReasons.filter(function (reason) { return reason.type == "REJECTED_LIABILITY"; });
                    var suppressedLiability = discardReasons.filter(function (reason) { return reason.type == "SUPPRESSED_LIABILITY"; });
                    var liabilityWithNoClassMethRecord = discardReasons.filter(function (reason) { return reason.type == "LIABILITY_WITH_NO_CLASS_METH_RECORD"; });

                    updateDiscardedCase(liabilityWithHistoricCase, "discarded-liability-historic-case");
                    updateDiscardedCase(rejectedLiability, "rejected-liability");
                    updateDiscardedCase(suppressedLiability, "suppressed-liability");
                    updateDiscardedCase(liabilityWithNoClassMethRecord, "liability-no-class-meth-record");

                    if (liabilityWithHistoricCase.length > 0 ||
                        rejectedLiability.length > 0 ||
                        suppressedLiability.length > 0 ||
                        liabilityWithNoClassMethRecord.length > 0) {
                        document.getElementById("liability-discarded-container").removeAttribute("aria-hidden");
                        document.getElementById("liability-discarded-container").removeAttribute("hidden");
                        document.getElementById("detailed-liability-discarded-cases-container").removeAttribute("aria-hidden");
                        document.getElementById("detailed-liability-discarded-cases-container").removeAttribute("hidden");
                    }
                }

                function updateDiscardedCase(discardReasons, prefix) {
                    if (discardReasons.length == 0) {
                        return;
                    }

                    document.getElementById(prefix + "-count").innerHTML = discardReasons.length
                    document.getElementById(prefix + "-row").removeAttribute("aria-hidden");
                    document.getElementById(prefix + "-row").removeAttribute("hidden");

                    document.getElementById(prefix + "-details").removeAttribute("aria-hidden");
                    document.getElementById(prefix + "-details").removeAttribute("hidden");

                    var listHtml = "";
                    discardReasons.forEach(function(reason) {
                        listHtml += "<li class=\"font-xsmall\">" + reason.caseNo + "</li>";
                    });

                    document.getElementById(prefix + "-list").innerHTML = listHtml;
                }

                function updateError(error) {
                    if (error !== undefined) {
                        document.getElementById("bti-processing-status").innerHTML = error;
                        document.getElementById("liabilities-processing-status").innerHTML = error;
                    } else {
                        document.getElementById("bti-processing-status").innerHTML = "Unknown error";
                        document.getElementById("liabilities-processing-status").innerHTML = "Unknown error";
                    }
                }

                function updateContinueMessage() {
                    var status = document.getElementById("bti-processing-status");
                    if(status.innerHTML == "done"){
                        document.getElementById("data_migration_bti_processing_status").removeAttribute("disabled");
                        document.getElementById("data_migration_liabilities_processing_status").removeAttribute("disabled");
                        document.getElementById("data_migration_migration-reports").removeAttribute("disabled");
                    }
                }
            }
};