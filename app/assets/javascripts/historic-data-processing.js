var historicDataProcessing = {

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
                        applCount: response.status.applCount,
                        btiCount: response.status.btiCount,
                        errorCount: response.status.errorCount,
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

            historicDataProcessing.checkStatus(url)
                .then(function (response) {
                    updateState(response.status);
                    updateContinueMessage();

                    updateApplCount(response.applCount);
                    updateBtiCount(response.btiCount);

                    updateErrorCount(response.errorCount);
                    updateErrors(response.errors);
                })
                .catch(function (response) {
                    updateError(response.error);
                })

        }

        function updateState(status) {
            if (status !== undefined) {
                document.getElementById("processing-status").innerHTML = status;
            }
        }

        function updateContinueMessage() {
            var status = document.getElementById("processing-status");
            if(status.innerHTML == "done"){
                document.getElementById("download-historic-json-button").removeAttribute("disabled");
            }
        }

        function updateApplCount(applCount) {
            if(applCount > 0) {
                document.getElementById("appl-count").innerHTML = applCount;
                document.getElementById("summary-container").removeAttribute("aria-hidden");
                document.getElementById("summary-container").removeAttribute("hidden");
            }
        }

        function updateBtiCount(btiCount) {
           if(btiCount > 0) {
               document.getElementById("bti-count").innerHTML = btiCount;
               document.getElementById("summary-container").removeAttribute("aria-hidden");
               document.getElementById("summary-container").removeAttribute("hidden");
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

        function updateError(error) {
            if (error !== undefined) {
                document.getElementById("processing-status").innerHTML = error;
            } else {
                document.getElementById("processing-status").innerHTML = "Unknown error";
            }
        }
    }
};
