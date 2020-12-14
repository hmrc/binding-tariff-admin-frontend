var historicDataTransformation = {

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
                        transformedApplCount: response.status.transformedApplCount,
                        transformedBtiCount: response.status.transformedBtiCount,
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

            historicDataTransformation.checkStatus(url)
                .then(function (response) {
                    updateState(response.status);
                    updateContinueMessage();

                    updateTransformedApplCount(response.transformedApplCount);
                    updateTransformedBtiCount(response.transformedBtiCount);

                    updateErrorCount(response.errorCount);
                    updateErrors(response.errors);
                })
                .catch(function (response) {
                    updateError(response.error);
                })

        }

        function updateState(status) {
            if (status !== undefined) {
                document.getElementById("transformation-status").innerHTML = status;
            }
        }

        function updateContinueMessage() {
            var status = document.getElementById("transformation-status");
            if(status.innerHTML == "done"){
                document.getElementById("download-transformed-historic-data-button").removeAttribute("disabled");
            }
        }

        function updateTransformedApplCount(transformedApplCount) {
            if(transformedApplCount > 0) {
                document.getElementById("transformed-appl-count").innerHTML = transformedApplCount;
                document.getElementById("summary-container").removeAttribute("aria-hidden");
                document.getElementById("summary-container").removeAttribute("hidden");
            }
        }

        function updateTransformedBtiCount(transformedBtiCount) {
              if(transformedBtiCount > 0) {
                  document.getElementById("transformed-bti-count").innerHTML = transformedBtiCount;
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
                document.getElementById("transformation-status").innerHTML = error;
            } else {
                document.getElementById("transformation-status").innerHTML = "Unknown error";
            }
        }
    }
};
