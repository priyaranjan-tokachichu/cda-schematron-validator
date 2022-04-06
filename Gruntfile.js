// jshint node:true
const path = require('path');
module.exports = function(grunt) {
    grunt.initConfig({
        jshint: {
            options: {
                jshintrc: path.resolve(__dirname, '.jshintrc')
            },
            files: {
                src: [path.resolve(__dirname, '*.js')]},
            gruntfile: {
                src: path.resolve(__dirname, 'Gruntfile.js')
            }
        },
        mochaTest: {
            api: {
                src: [path.resolve(__dirname, 'test/*test*.js')],
                options: {
                    reporter: 'spec'
                }
            }
        }
    });
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-mocha-test');
    grunt.registerTask('default', ['jshint', 'mochaTest']);
    grunt.registerTask('api', ['mochaTest']);
};
