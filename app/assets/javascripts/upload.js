var fileMigration = {
    uploadAll: function upload(url) {

        var input = document.getElementById("files");
        incrementTotal(input.files.length);
        showProgress();

        for (var i = 0; i < input.files.length; i++) {
            var file = input.files[i];
            fileMigration.initiate(url, file)
                .then(function (response) {
                    fileMigration.uploadToS3(response.file, response.template)
                        .then(function () {
                            incrementCount();
                            appendToSuccessTable(response.file);
                        })
                        .catch(function(err){appendErrorToFailedTable(err)})
                })
                .catch(function(err){appendErrorToFailedTable(err)})
        }

        function showProgress() {
            var progress = document.getElementById("progress");
            progress.classList.remove("display-none");
        }

        function incrementCount() {
            var progressCount = document.getElementById("progress-count");
            progressCount.innerHTML = parseInt(progressCount.innerHTML) + 1;
        }

        function incrementTotal(count) {
            var progressTotal = document.getElementById("progress-total");
            progressTotal.innerHTML = parseInt(progressTotal.innerHTML) + count;
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
            if(error.file){
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

    initiate: function initiate(url, file) {
        var filename = file.name;
        var type = file.type;

        return new Promise(function (resolve, reject) {
            var csrf = document.getElementsByName("csrfToken").item(0);
            $.ajax({
                type: "POST",
                url: url,
                headers: {
                    "CSRF-token": csrf.value
                },
                data: JSON.stringify({
                    id: filename,
                    fileName: filename,
                    mimeType: type
                }),
                contentType: "application/json",
                success: function (json) {
                    resolve({
                        file: file,
                        template: JSON.parse(json)
                    })
                },
                error: function (response) {
                    reject({
                        file: file,
                        message: response.statusText
                    });
                }
            })
        })
    },

    uploadToS3: function uploadToS3(file, template) {
        var form = new FormData();
        Object.keys(template.fields).forEach(function (key) {
            form.append(key, template.fields[key]);
        });
        form.append("file", file);

        return new Promise(function (resolve, reject) {
            $.ajax({
                type: "POST",
                url: template.href,
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
    }
};