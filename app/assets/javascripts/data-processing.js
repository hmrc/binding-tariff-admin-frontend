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
                        discardReasons: response.status.discardReasons
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
                            updateErrorCount(response.errorCount);
                            updateDiscardedBtiCount(response.discardedBtiCount);
                            updateDiscardedLiabilityCount(response.discardedLiabilityCount);
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
                    if (discardReasons === undefined) {
                        return;
                    }

                    var btiWithHistoricCase = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_HISTORIC_CASE"; });
                    var rejectedBti = discardReasons.filter(function (reason) { return reason.type == "REJECTED_BTI"; });
                    var suppressedBti = discardReasons.filter(function (reason) { return reason.type == "SUPPRESSED_BTI"; });
                    var btiWithNoApplicationRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_APPLICATION_RECORD"; });
                    var btiWithNoCaseBtiRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_CASE_BTI_RECORD"; });
                    var btiWithNoClassMethRecord = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_CLASS_METH_RECORD"; });
                    var btiWithNoAddressRecords = discardReasons.filter(function (reason) { return reason.type == "BTI_WITH_NO_ADDRESS_RECORDS"; });

                    if(btiWithHistoricCase.length > 0) {
                        document.getElementById("discarded-bti-historic-case-count").innerHTML = btiWithHistoricCase.length
                        document.getElementById("discarded-bti-historic-case-row").removeAttribute("aria-hidden");
                        document.getElementById("discarded-bti-historic-case-row").removeAttribute("hidden");
                    }

                    if(rejectedBti.length > 0) {
                        document.getElementById("rejected-bti-count").innerHTML = rejectedBti.length
                        document.getElementById("rejected-bti-row").removeAttribute("aria-hidden");
                        document.getElementById("rejected-bti-row").removeAttribute("hidden");
                    }

                    if(suppressedBti.length > 0) {
                        document.getElementById("suppressed-bti-count").innerHTML = suppressedBti.length
                        document.getElementById("suppressed-bti-row").removeAttribute("aria-hidden");
                        document.getElementById("suppressed-bti-row").removeAttribute("hidden");
                    }

                    if(btiWithNoApplicationRecord.length > 0) {
                        document.getElementById("bti-no-application-record-count").innerHTML = btiWithNoApplicationRecord.length
                        document.getElementById("bti-no-application-record-row").removeAttribute("aria-hidden");
                        document.getElementById("bti-no-application-record-row").removeAttribute("hidden");
                    }

                    if(btiWithNoCaseBtiRecord.length > 0) {
                        document.getElementById("bti-no-case-bti-record-count").innerHTML = btiWithNoCaseBtiRecord.length
                        document.getElementById("bti-no-case-bti-record-row").removeAttribute("aria-hidden");
                        document.getElementById("bti-no-case-bti-record-row").removeAttribute("hidden");
                    }

                    if(btiWithNoClassMethRecord.length > 0) {
                        document.getElementById("bti-no-class-meth-record-count").innerHTML = btiWithNoClassMethRecord.length
                        document.getElementById("bti-no-class-meth-record-row").removeAttribute("aria-hidden");
                        document.getElementById("bti-no-class-meth-record-row").removeAttribute("hidden");
                    }

                    if(btiWithNoAddressRecords.length > 0) {
                        document.getElementById("bti-no-address-records-count").innerHTML = btiWithNoAddressRecords.length
                        document.getElementById("bti-no-address-records-row").removeAttribute("aria-hidden");
                        document.getElementById("bti-no-address-records-row").removeAttribute("hidden");
                    }

                    if (btiWithHistoricCase.length > 0 ||
                        rejectedBti.length > 0 ||
                        suppressedBti.length > 0 ||
                        btiWithNoApplicationRecord.length > 0 ||
                        btiWithNoCaseBtiRecord.length > 0 ||
                        btiWithNoClassMethRecord.length > 0 ||
                        btiWithNoAddressRecords.length > 0) {
                        document.getElementById("bti-discarded-container").removeAttribute("aria-hidden");
                        document.getElementById("bti-discarded-container").removeAttribute("hidden");
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

                    if(liabilityWithHistoricCase.length > 0) {
                        document.getElementById("discarded-liability-historic-case-count").innerHTML = liabilityWithHistoricCase.length
                        document.getElementById("discarded-liability-historic-case-row").removeAttribute("aria-hidden");
                        document.getElementById("discarded-liability-historic-case-row").removeAttribute("hidden");
                    }

                    if(rejectedLiability.length > 0) {
                        document.getElementById("rejected-liability-count").innerHTML = rejectedLiability.length
                        document.getElementById("rejected-liability-row").removeAttribute("aria-hidden");
                        document.getElementById("rejected-liability-row").removeAttribute("hidden");
                    }

                    if(suppressedLiability.length > 0) {
                        document.getElementById("suppressed-liability-count").innerHTML = suppressedLiability.length
                        document.getElementById("suppressed-liability-row").removeAttribute("aria-hidden");
                        document.getElementById("suppressed-liability-row").removeAttribute("hidden");
                    }

                    if(liabilityWithNoClassMethRecord.length > 0) {
                        document.getElementById("liability-no-class-meth-record-count").innerHTML = liabilityWithNoClassMethRecord.length
                        document.getElementById("liability-no-class-meth-record-row").removeAttribute("aria-hidden");
                        document.getElementById("liability-no-class-meth-record-row").removeAttribute("hidden");
                    }

                    if (liabilityWithHistoricCase.length > 0 ||
                        rejectedLiability.length > 0 ||
                        suppressedLiability.length > 0 ||
                        liabilityWithNoClassMethRecord.length > 0) {
                        document.getElementById("liability-discarded-container").removeAttribute("aria-hidden");
                        document.getElementById("liability-discarded-container").removeAttribute("hidden");
                    }
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
                    }
                }
            }
};