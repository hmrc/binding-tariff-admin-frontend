var fileMigration = {
    uploadAll: function upload(url) {
        var files = document.getElementById("files");
        var folders = document.getElementById("folders");
        uploadAll(files);
        uploadAll(folders);

        function uploadAll(input) {
            if (input.files.length === 0) {
                return;
            }

            incrementTotal(input.files.length);
            showProgress();

            for (var i = 0; i < input.files.length; i++) {
                var file = input.files[i];
                fileMigration.upload(url, file)
                    .then(function (response) {
                        incrementSuccessCount();
                        appendToSuccessTable(response.file);
                    })
                    .catch(function (err) {
                        incrementFailureCount();
                        appendErrorToFailedTable(err);
                    })
                    .finally(updateState)
            }
        }

        function updateState() {
            var state = document.getElementById("progress-state");

            var success = parseInt(document.getElementsByClassName("success-count")[0].innerHTML);
            var failed = parseInt(document.getElementsByClassName("failed-count")[0].innerHTML);
            var total = parseInt(document.getElementsByClassName("total-count")[0].innerHTML);

            if (total === success + failed) {
                state.innerHTML = "Uploaded";
            } else {
                state.innerHTML = "Uploading";
            }
        }

        function showProgress() {
            var progress = document.getElementById("progress");
            progress.classList.remove("display-none");
        }

        function incrementSuccessCount() {
            var counts = document.getElementsByClassName("success-count");
            for (var i = 0; i < counts.length; i++) {
                counts[i].innerHTML = parseInt(counts[i].innerHTML) + 1;
            }
        }

        function incrementFailureCount() {
            var counts = document.getElementsByClassName("failed-count");
            for (var i = 0; i < counts.length; i++) {
                counts[i].innerHTML = parseInt(counts[i].innerHTML) + 1;
            }
        }

        function incrementTotal(count) {
            var counts = document.getElementsByClassName("total-count");
            for (var i = 0; i < counts.length; i++) {
                counts[i].innerHTML = parseInt(counts[i].innerHTML) + count;
            }
        }

        function appendToSuccessTable(file) {
            var container = document.getElementById("success-table-container");
            container.classList.remove("display-none");

            var table = document.getElementById("success-table");
            var tr = document.createElement("tr");
            var th = document.createElement("th");

            th.innerText = file.name;
            tr.appendChild(th);
            table.appendChild(tr)
        }

        function appendErrorToFailedTable(error) {
            if (error.file) {
                var container = document.getElementById("failed-table-container");
                container.classList.remove("display-none");
                var table = document.getElementById("failed-table");
                var tr = document.createElement("tr");
                var th = document.createElement("th");
                var td = document.createElement("td");
                th.innerText = error.file.name;
                td.innerText = error.message;
                tr.appendChild(th);
                tr.appendChild(td);
                table.appendChild(tr)
            } else {
                console.error(error);
            }
        }
    },

    upload: function initiate(url, file) {
        var filename = file.name;
        var type = file.type;

        var form = new FormData();
        form.append("filename", filename);
        form.append("mimetype", type);
        form.append("file", file);

        return new Promise(function (resolve, reject) {
            $.ajax({
                type: "POST",
                url: url,
                data: form,
                processData: false,
                contentType: false,
                mimeType: "multipart/form-data",
                success: function () {
                    resolve({
                        file: file
                    });
                },
                error: function (response) {
                    reject({
                        file: file,
                        message: response.statusText
                    });
                }
            })
        });
    },

    uploadData: function upload(url) {
            var files = document.getElementById("files");
            uploadAllData(files);

            function uploadAllData(input) {
                if (input.files.length === 0) {
                    return;
                }

                incrementTotal(input.files.length);
                showProgress();

                for (var i = 0; i < input.files.length; i++) {
                    var file = input.files[i];
                    fileMigration.upload(url, file)
                        .then(function (response) {
                            incrementSuccessCount();
                            updateContinueMessage(input.files.length);
                            appendToSuccessTableData(response.file);
                        })
                        .catch(function (err) {
                            incrementFailureCount();
                            appendErrorToFailedTable(err);
                        })
                        .finally(fileMigration.updateState)
                }
            }


            function updateState() {
                var state = document.getElementById("progress-state");

                var success = parseInt(document.getElementsByClassName("success-count")[0].innerHTML);
                var failed = parseInt(document.getElementsByClassName("failed-count")[0].innerHTML);
                var total = parseInt(document.getElementsByClassName("total-count")[0].innerHTML);

                if (total === success + failed) {
                    state.innerHTML = "Uploaded";
                } else {
                    state.innerHTML = "Uploading";
                }
            }

            function updateContinueMessage(total) {
                var count = document.getElementById("success-count");
                if(parseInt(count.innerHTML) == total){
                    var data_migration_upload = document.getElementById("data_migration_upload-continue");
                    data_migration_upload.classList.remove("display-none");
                }
            }

            function showProgress() {
                var progress = document.getElementById("progress");
                progress.classList.remove("display-none");
            }

            function incrementSuccessCount() {
                var counts = document.getElementsByClassName("success-count");
                for (var i = 0; i < counts.length; i++) {
                    counts[i].innerHTML = parseInt(counts[i].innerHTML) + 1;
                }
            }

            function incrementFailureCount() {
                var counts = document.getElementsByClassName("failed-count");
                for (var i = 0; i < counts.length; i++) {
                    counts[i].innerHTML = parseInt(counts[i].innerHTML) + 1;
                }
            }

            function incrementTotal(count) {
                var counts = document.getElementsByClassName("total-count");
                for (var i = 0; i < counts.length; i++) {
                    counts[i].innerHTML = parseInt(counts[i].innerHTML) + count;
                }
            }

            function appendToSuccessTableData(file) {
                var container = document.getElementById("success-table-container");
                container.classList.remove("display-none");

                var table = document.getElementById("success-table");
                var tr = document.createElement("tr");
                var th = document.createElement("th");

                th.innerText = file.name;
                tr.appendChild(th);
                table.appendChild(tr)
            }

            function appendErrorToFailedTable(error) {
                        if (error.file) {
                            var container = document.getElementById("failed-table-container");
                            container.classList.remove("display-none");
                            var table = document.getElementById("failed-table");
                            var tr = document.createElement("tr");
                            var th = document.createElement("th");
                            var td = document.createElement("td");
                            th.innerText = error.file.name;
                            td.innerText = error.message;
                            tr.appendChild(th);
                            tr.appendChild(td);
                            table.appendChild(tr)
                        } else {
                            console.error(error);
                        }
                    }
        }
};