module.exports = {
    'env': {
        'browser': true,
        'commonjs': true,
        'es2021': true
    },
    'extends': [
        'google'
    ],
    'parserOptions': {
        'ecmaVersion': 'latest'
    },
    'rules': {
        'comma-dangle': ['error', 'never'],
        'linebreak-style': 0,
        'indent': ['error', 4],
        'require-jsdoc': 0,
        'object-curly-spacing': 0,
        'func-call-spacing': 0,
        'max-len': [2, {
            'code': 200,
            'tabWidth': 4,
            'ignoreUrls': true,
            'ignorePattern': 'goog\.(module|require)',
            'ignoreStrings': true,
            'ignoreComments': true
        }],
        'brace-style': ['error', 'stroustrup']
    }
};
