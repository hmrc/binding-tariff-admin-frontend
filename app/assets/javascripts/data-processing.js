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
                        status: response.status,
                        errorCount: response.errorCount
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
                        .then(function (status) {
                            updateState(status);
                            updateContinueMessage();
                            updateErrorCount(status);
                        })
                        .catch(function (err) {
                            updateError(err);
                        })

                }

                function updateState(response) {
                    document.getElementById("bti-processing-status").innerHTML = response.status;
                    document.getElementById("liabilities-processing-status").innerHTML = response.status;
                }

                function updateErrorCount(response) {
                    if(response.errorCount > 0) {

                        document.getElementById("error-count").innerHTML = response.errorCount;
                        document.getElementById("error-row").removeAttribute("aria-hidden");
                        document.getElementById("error-row").removeAttribute("hidden");
                    }
                }

                function updateError(response) {
                    document.getElementById("bti-processing-status").innerHTML = response.error;
                    document.getElementById("liabilities-processing-status").innerHTML = response.error;
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