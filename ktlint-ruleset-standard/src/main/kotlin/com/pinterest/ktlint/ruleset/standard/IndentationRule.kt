package com.pinterest.ktlint.ruleset.standard

import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.CONSTRUCTOR_DELEGATION_CALL
import com.pinterest.ktlint.core.ast.ElementType.SUPER_TYPE_LIST
import com.pinterest.ktlint.core.ast.ElementType.TYPE_REFERENCE
import com.pinterest.ktlint.core.ast.isPartOf
import com.pinterest.ktlint.core.ast.isRoot
import com.pinterest.ktlint.core.ast.nextLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class IndentationRule : Rule("indent") {

    private var indentSize = -1
    private var continuationIndentSize = -1

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        if (node.isRoot()) {
            val editorConfig = node.getUserData(KtLint.EDITOR_CONFIG_USER_DATA_KEY)!!
            indentSize = editorConfig.indentSize
            continuationIndentSize = editorConfig.continuationIndentSize
            return
        }
        if (indentSize <= 1) {
            return
        }
        if (node is PsiWhiteSpace && !node.isPartOf(PsiComment::class)) {
            val lines = node.getText().split("\n")
            if (lines.size > 1 && !node.isPartOf(KtTypeConstraintList::class)) {
                var offset = node.startOffset + lines.first().length + 1
                val previousIndentSize = node.previousIndentSize()
                val expectedIndentSize = if (continuationIndentSize == indentSize || shouldUseContinuationIndent(node))
                    continuationIndentSize else indentSize
                lines.tail().forEach { indent ->
                    if (indent.isNotEmpty() && (indent.length - previousIndentSize) % expectedIndentSize != 0) {
                        if (!node.isPartOf(KtParameterList::class)) { // parameter list wrapping enforced by ParameterListWrappingRule
                            emit(
                                offset,
                                "Unexpected indentation (${indent.length}) (it should be ${previousIndentSize + expectedIndentSize})",
                                false
                            )
                        }
                    }
                    offset += indent.length + 1
                }
            }
            if (node.textContains('\t')) {
                val text = node.getText()
                emit(node.startOffset + text.indexOf('\t'), "Unexpected Tab character(s)", true)
                if (autoCorrect) {
                    (node as LeafPsiElement).rawReplaceWithText(text.replace("\t", " ".repeat(indentSize)))
                }
            }
        }
    }

    private fun shouldUseContinuationIndent(node: PsiWhiteSpace): Boolean {
        val parentNode = node.parent
        val prevNode = node.getPrevSiblingIgnoringWhitespaceAndComments()?.node?.elementType
        val nextNode = node.nextSibling?.node?.elementType
        return (
            prevNode in KtTokens.ALL_ASSIGNMENTS ||
                parentNode is KtSecondaryConstructor ||
                nextNode == KtStubElementTypes.TYPE_REFERENCE ||
                node.nextSibling is KtSuperTypeList ||
                node.nextSibling is KtSuperTypeListEntry ||
                node.nextSibling is KtTypeProjection ||
                parentNode is KtValueArgumentList ||
                parentNode is KtBinaryExpression ||
                parentNode is KtDotQualifiedExpression ||
                parentNode is KtSafeQualifiedExpression ||
                parentNode is KtParenthesizedExpression
            )
    }

    // todo: calculating indent based on the previous line value is wrong (see IndentationRule.testLint)
    private fun ASTNode.previousIndentSize(): Int {
        var node: ASTNode? = this.treeParent
        while (node != null) {
            val nextNode = node.treeNext?.elementType
            if (node is PsiWhiteSpace &&
                nextNode != TYPE_REFERENCE &&
                nextNode != SUPER_TYPE_LIST &&
                nextNode != CONSTRUCTOR_DELEGATION_CALL &&
                node.textContains('\n') &&
                node.nextLeaf()?.isPartOf(PsiComment::class) != true
            ) {
                val text = node.getText()
                return text.length - text.lastIndexOf('\n') - 1
            }
            node = node.treePrev ?: node.treeParent
        }
        return 0
    }
}
