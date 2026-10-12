const editor =
    document.getElementById("editor");

const language =
    document.getElementById("language");

const checkBtn =
    document.getElementById("checkBtn");

const clearBtn =
    document.getElementById("clearBtn");

const wordCount =
    document.getElementById("wordCount");

const errorCount =
    document.getElementById("errorCount");

const resultList =
    document.getElementById("resultList");

const suggestionPopup =
    document.getElementById("suggestionPopup");


let currentErrors = [];


/* CHECK BUTTON */

checkBtn.addEventListener(
    "click",
    checkText
);


/* CLEAR BUTTON */

clearBtn.addEventListener(
    "click",
    clearAll
);


/* EDITOR */

editor.addEventListener(
    "input",
    () => {

        updateWordCount();

        hidePopup();
    }
);


/* CLOSE POPUP */

document.addEventListener(
    "click",
    event => {

        if (
            !suggestionPopup.contains(
                event.target
            ) &&
            !editor.contains(
                event.target
            )
        ) {

            hidePopup();
        }
    }
);


/* WORD COUNT */

function updateWordCount() {

    const text =
        editor.innerText.trim();

    if (!text) {

        wordCount.textContent = "0";

        return;
    }

    const words =
        text.match(
            /[\p{L}\p{M}]+/gu
        );

    wordCount.textContent =
        words ? words.length : 0;
}


/* CHECK TEXT */

async function checkText() {

    const text =
        editor.innerText.trim();

    if (!text) {

        alert(
            "Please enter some text."
        );

        return;
    }

    checkBtn.disabled = true;

    checkBtn.textContent =
        "Checking...";


    try {

        const url =
            "/check?lang=" +
            encodeURIComponent(
                language.value
            ) +
            "&text=" +
            encodeURIComponent(
                text
            );


        const response =
            await fetch(url);


        if (!response.ok) {

            throw new Error(
                "Server error"
            );
        }


        const data =
            await response.json();


        currentErrors =
            data.errors || [];


        errorCount.textContent =
            currentErrors.length;


        showHighlightedText(
            text,
            currentErrors
        );


        showErrorList(
            currentErrors
        );


    } catch (error) {

        console.error(error);

        alert(
            "Could not connect to the spell checker."
        );


    } finally {

        checkBtn.disabled = false;

        checkBtn.textContent =
            "🔍 Check Spelling";
    }
}


/* HIGHLIGHT WRONG WORDS */

function showHighlightedText(
    text,
    errors
) {

    if (!errors.length) {

        editor.innerHTML =
            escapeHtml(text);

        return;
    }


    let html = "";

    let lastIndex = 0;


    errors.forEach(
        (error, index) => {

            html +=
                escapeHtml(
                    text.substring(
                        lastIndex,
                        error.start
                    )
                );


            html += `
                <span
                    class="wrong-word"
                    data-index="${index}"
                >
                    ${escapeHtml(
                        error.word
                    )}
                </span>
            `;


            lastIndex =
                error.end;
        }
    );


    html +=
        escapeHtml(
            text.substring(
                lastIndex
            )
        );


    editor.innerHTML =
        html;


    document
        .querySelectorAll(
            ".wrong-word"
        )
        .forEach(
            element => {

                element.addEventListener(
                    "click",
                    event => {

                        event.stopPropagation();


                        const index =
                            Number(
                                element.dataset.index
                            );


                        showSuggestions(
                            element,
                            currentErrors[
                                index
                            ]
                        );
                    }
                );
            }
        );
}


/* SHOW SUGGESTIONS */

function showSuggestions(
    element,
    error
) {

    suggestionPopup.innerHTML =
        "";


    const title =
        document.createElement(
            "div"
        );


    title.className =
        "popup-title";


    title.textContent =
        `Suggestions for "${error.word}"`;


    suggestionPopup.appendChild(
        title
    );


    if (
        !error.suggestions ||
        error.suggestions.length === 0
    ) {

        const message =
            document.createElement(
                "div"
            );


        message.className =
            "no-suggestion";


        message.textContent =
            "No close suggestion found.";


        suggestionPopup.appendChild(
            message
        );

    } else {

        error.suggestions.forEach(
            suggestion => {

                const button =
                    document.createElement(
                        "button"
                    );


                button.className =
                    "suggestion-button";


                button.innerHTML = `
                    <span>
                        ${escapeHtml(
                            suggestion.word
                        )}
                    </span>

                    <small>
                        Distance:
                        ${suggestion.distance}
                    </small>
                `;


                button.addEventListener(
                    "click",
                    () => {

                        replaceWord(
                            error,
                            suggestion.word
                        );

                        hidePopup();
                    }
                );


                suggestionPopup.appendChild(
                    button
                );
            }
        );
    }


    const rect =
        element.getBoundingClientRect();


    suggestionPopup.style.display =
        "block";


    suggestionPopup.style.left =
        `${rect.left +
        window.scrollX}px`;


    suggestionPopup.style.top =
        `${rect.bottom +
        window.scrollY +
        8}px`;
}


/* REPLACE WORD */

function replaceWord(
    error,
    replacement
) {

    let text =
        editor.innerText;


    text =
        text.substring(
            0,
            error.start
        ) +

        replacement +

        text.substring(
            error.end
        );


    editor.innerText =
        text;


    updateWordCount();


    checkText();
}


/* RESULT LIST */

function showErrorList(
    errors
) {

    resultList.innerHTML =
        "";


    if (!errors.length) {

        resultList.innerHTML = `
            <div class="success-message">
                ✓ No spelling mistakes found!
            </div>
        `;

        return;
    }


    errors.forEach(
        (error, index) => {

            const item =
                document.createElement(
                    "div"
                );


            item.className =
                "error-item";


            const suggestions =
                error.suggestions || [];


            let suggestionText =
                "No suggestions";


            if (
                suggestions.length
            ) {

                suggestionText =
                    suggestions
                        .slice(0, 3)
                        .map(
                            suggestion =>
                                `${suggestion.word}
                                (${suggestion.distance})`
                        )
                        .join(", ");
            }


            item.innerHTML = `
                <div class="error-word">
                    ${escapeHtml(
                        error.word
                    )}
                </div>

                <div class="error-suggestions">
                    ${escapeHtml(
                        suggestionText
                    )}
                </div>
            `;


            item.addEventListener(
                "click",
                () => {

                    const element =
                        document.querySelector(
                            `.wrong-word[data-index="${index}"]`
                        );


                    if (element) {

                        showSuggestions(
                            element,
                            error
                        );
                    }
                }
            );


            resultList.appendChild(
                item
            );
        }
    );
}


/* CLEAR */

function clearAll() {

    editor.innerHTML =
        "";


    currentErrors =
        [];


    errorCount.textContent =
        "0";


    wordCount.textContent =
        "0";


    resultList.innerHTML = `
        <div class="empty-message">
            Enter text and click
            <b>Check Spelling</b>.
        </div>
    `;


    hidePopup();


    editor.focus();
}


/* HIDE POPUP */

function hidePopup() {

    suggestionPopup.style.display =
        "none";
}


/* ESCAPE HTML */

function escapeHtml(text) {

    return text
        .replace(
            /&/g,
            "&amp;"
        )
        .replace(
            /</g,
            "&lt;"
        )
        .replace(
            />/g,
            "&gt;"
        )
        .replace(
            /"/g,
            "&quot;"
        )
        .replace(
            /'/g,
            "&#039;"
        );
}


/* INITIAL */

updateWordCount();