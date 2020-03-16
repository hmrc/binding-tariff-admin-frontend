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
                        status: response.status
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
                        })
                        .catch(function (err) {
                            updateError(err);
                        })

                }

                function updateState(response) {
                   var status = document.getElementById("processing-status");
                    status.innerHTML = response.status;
                }

                function updateError(response) {
                   var status = document.getElementById("processing-status");
                    status.innerHTML = response.error;
                }

                function updateContinueMessage() {
                    var status = document.getElementById("processing-status");
                    if(status.innerHTML == "done"){
                        var data_migration_upload = document.getElementById("data_migration_processing-status");
                        data_migration_upload.removeAttribute("disabled");
                    }
                }
            }
};